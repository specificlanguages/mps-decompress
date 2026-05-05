package validate

import (
	"bytes"
	"encoding/json"
	"encoding/xml"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"mops/internal/nodeids"
	"mops/internal/xmlschema"
)

type Report struct {
	Status   string         `json:"status"`
	Findings []Finding      `json:"findings"`
	Targets  []TargetReport `json:"-"`
}

type TargetReport struct {
	Target   string
	Findings []Finding
}

type Finding struct {
	Severity string    `json:"severity"`
	Code     string    `json:"code"`
	Message  string    `json:"message"`
	Target   string    `json:"target"`
	File     string    `json:"file,omitempty"`
	Location *Location `json:"location,omitempty"`
	Layer    string    `json:"layer"`
	Source   string    `json:"source"`
}

type Location struct {
	Line   int `json:"line"`
	Column int `json:"column"`
}

func Run(targets []string) Report {
	report := Report{}
	for _, target := range targets {
		targetReport := validateTarget(target)
		report.Targets = append(report.Targets, targetReport)
		report.Findings = append(report.Findings, targetReport.Findings...)
	}
	report.Status = "ok"
	if report.HasErrors() {
		report.Status = "failed"
	}
	return report
}

func WriteJSON(report Report, output *json.Encoder) error {
	return output.Encode(report)
}

func (r Report) HasErrors() bool {
	for _, finding := range r.Findings {
		if finding.Severity == "error" {
			return true
		}
	}
	return false
}

func validateTarget(path string) TargetReport {
	target := TargetReport{Target: filepath.ToSlash(path)}
	info, err := os.Stat(path)
	if err != nil {
		target.Findings = append(target.Findings, target.finding("error", "target-read-failed", fmt.Sprintf("read target: %v", err), nil, "filesystem"))
		return target
	}
	if info.IsDir() {
		validateFilePerRootFolder(path, &target)
		return target
	}
	if filepath.Ext(path) == ".mpsr" {
		validateStandaloneRootFile(path, &target)
		return target
	}

	validateStandaloneFile(path, &target)
	return target
}

func validateFilePerRootFolder(path string, target *TargetReport) {
	seenNodeIDs := map[string]struct{}{}
	validateXMLFile(filepath.Join(path, ".model"), target, seenNodeIDs, true, true)

	entries, err := os.ReadDir(path)
	if err != nil {
		target.Findings = append(target.Findings, target.finding("error", "target-read-failed", fmt.Sprintf("read target: %v", err), nil, "filesystem"))
		return
	}
	for _, entry := range entries {
		if entry.IsDir() || filepath.Ext(entry.Name()) != ".mpsr" {
			continue
		}
		validateXMLFile(filepath.Join(path, entry.Name()), target, seenNodeIDs, false, true)
	}
}

func validateStandaloneFile(path string, target *TargetReport) {
	seenNodeIDs := map[string]struct{}{}
	validateXMLFile(path, target, seenNodeIDs, true, false)
}

func validateStandaloneRootFile(path string, target *TargetReport) {
	modelFolder := filepath.ToSlash(filepath.Dir(path))
	target.Findings = append(target.Findings, target.finding(
		"info",
		"incomplete-validation",
		fmt.Sprintf("standalone root file validated without model-wide context; for complete structural validation, run mops validate %s", modelFolder),
		nil,
		"validation-target",
	))
	seenNodeIDs := map[string]struct{}{}
	validateXMLFile(path, target, seenNodeIDs, false, false)
}

func validateXMLFile(path string, target *TargetReport, seenNodeIDs map[string]struct{}, requirePersistence, reportFile bool) {
	file := ""
	if reportFile {
		file = filepath.ToSlash(path)
	}
	data, err := os.ReadFile(path)
	if err != nil {
		target.Findings = append(target.Findings, target.findingInFile("error", "target-read-failed", fmt.Sprintf("read target: %v", err), nil, "filesystem", file))
		return
	}

	persistenceVersion := ""
	decoder := xml.NewDecoder(bytes.NewReader(data))
	depth := 0
	rootClosed := false
	var elementStack []string
	for {
		token, err := decoder.Token()
		if err != nil {
			if err == io.EOF {
				break
			}
			line, column := decoder.InputPos()
			target.Findings = append(target.Findings, target.malformedXMLFinding(err, &Location{Line: line, Column: column}, file))
			return
		}

		switch token := token.(type) {
		case xml.StartElement:
			if rootClosed {
				line, column := decoder.InputPos()
				target.Findings = append(target.Findings, target.malformedXMLFinding(
					fmt.Errorf("trailing content after document element"),
					&Location{Line: line, Column: column},
					file,
				))
				return
			}
			validateNoXMLNamespace(target, token, file)
			depth++
			validateContainmentShape(target, token, elementStack, requirePersistence, file)
			switch token.Name.Local {
			case "persistence":
				persistenceVersion = attr(token, "version")
			case "node":
				validateNodeID(target, seenNodeIDs, attr(token, "id"), file)
			}
			elementStack = append(elementStack, token.Name.Local)
		case xml.EndElement:
			if depth > 0 {
				depth--
			}
			if len(elementStack) > 0 {
				elementStack = elementStack[:len(elementStack)-1]
			}
			if depth == 0 {
				rootClosed = true
			}
		case xml.CharData:
			if rootClosed && strings.TrimSpace(string(token)) != "" {
				line, column := decoder.InputPos()
				target.Findings = append(target.Findings, target.malformedXMLFinding(
					fmt.Errorf("trailing content after document element"),
					&Location{Line: line, Column: column},
					file,
				))
				return
			}
		}
	}

	if err := xmlschema.ValidateMPSPersistence(data); err != nil {
		target.Findings = append(target.Findings, target.findingInFile(
			"error",
			"invalid-persistence-grammar",
			err.Error(),
			nil,
			"relax-ng",
			file,
		))
	}

	if persistenceVersion != "9" {
		target.Findings = append(target.Findings, target.findingInFile(
			"error",
			"unsupported-persistence-version",
			fmt.Sprintf("unsupported persistence version %q", persistenceVersion),
			nil,
			"persistence",
			file,
		))
	}
}

func validateNoXMLNamespace(target *TargetReport, start xml.StartElement, file string) {
	if start.Name.Space != "" {
		target.Findings = append(target.Findings, target.findingInFile(
			"error",
			"unsupported-xml-namespace",
			fmt.Sprintf("unsupported XML namespace %q on <%s>", start.Name.Space, start.Name.Local),
			nil,
			"persistence-grammar",
			file,
		))
	}
	for _, attr := range start.Attr {
		if attr.Name.Space == "" {
			continue
		}
		target.Findings = append(target.Findings, target.findingInFile(
			"error",
			"unsupported-xml-namespace",
			fmt.Sprintf("unsupported XML namespace %q on attribute %s", attr.Name.Space, attr.Name.Local),
			nil,
			"persistence-grammar",
			file,
		))
	}
}

func validateContainmentShape(target *TargetReport, start xml.StartElement, elementStack []string, requireModelRoot bool, file string) {
	parent := ""
	if len(elementStack) > 0 {
		parent = elementStack[len(elementStack)-1]
	}
	isDocumentRoot := len(elementStack) == 0
	inNodeGraph := parent == "node" || parent == "model" || (isDocumentRoot && !requireModelRoot)

	if isDocumentRoot {
		if requireModelRoot && start.Name.Local != "model" {
			target.Findings = append(target.Findings, target.findingInFile(
				"error",
				"invalid-document-root",
				fmt.Sprintf("document root must be <model>, got <%s>", start.Name.Local),
				nil,
				"persistence-grammar",
				file,
			))
		}
		if !requireModelRoot && start.Name.Local != "model" {
			target.Findings = append(target.Findings, target.findingInFile(
				"error",
				"invalid-document-root",
				fmt.Sprintf("root file document root must be <model>, got <%s>", start.Name.Local),
				nil,
				"persistence-grammar",
				file,
			))
		}
	}

	switch start.Name.Local {
	case "node":
		validateNodeContainmentShape(target, start, parent, isDocumentRoot, requireModelRoot, file)
	case "property":
		if !inNodeGraph {
			return
		}
		if parent != "node" {
			target.Findings = append(target.Findings, target.findingInFile(
				"error",
				"misplaced-property",
				"property element must appear under a node",
				nil,
				"persistence-grammar",
				file,
			))
		}
		if parent == "node" && attr(start, "role") == "" {
			target.Findings = append(target.Findings, target.findingInFile(
				"error",
				"missing-property-role",
				"property must have a property role",
				nil,
				"persistence-grammar",
				file,
			))
		}
	case "ref":
		if !inNodeGraph {
			return
		}
		if parent != "node" {
			target.Findings = append(target.Findings, target.findingInFile(
				"error",
				"misplaced-reference",
				"ref element must appear under a node",
				nil,
				"persistence-grammar",
				file,
			))
		}
		if parent == "node" && attr(start, "role") == "" {
			target.Findings = append(target.Findings, target.findingInFile(
				"error",
				"missing-reference-role",
				"ref must have a reference role",
				nil,
				"persistence-grammar",
				file,
			))
		}
	}
}

func validateNodeContainmentShape(target *TargetReport, start xml.StartElement, parent string, isDocumentRoot, requireModelRoot bool, file string) {
	role := attr(start, "role")
	isRootNode := parent == "model" || (isDocumentRoot && !requireModelRoot)
	isChildNode := parent == "node"

	switch {
	case isRootNode:
		if role != "" {
			target.Findings = append(target.Findings, target.findingInFile(
				"error",
				"unexpected-root-role",
				"root node must not have a child role",
				nil,
				"persistence-grammar",
				file,
			))
		}
	case isChildNode:
		if role == "" {
			target.Findings = append(target.Findings, target.findingInFile(
				"error",
				"missing-child-role",
				"child node must have a child role",
				nil,
				"persistence-grammar",
				file,
			))
		}
	default:
		target.Findings = append(target.Findings, target.findingInFile(
			"error",
			"misplaced-node",
			"node element must appear as a model root or under another node",
			nil,
			"persistence-grammar",
			file,
		))
	}
}

func validateNodeID(target *TargetReport, seenNodeIDs map[string]struct{}, id, file string) {
	if id == "^" {
		target.Findings = append(target.Findings, target.findingInFile(
			"error",
			"forbidden-dynamic-node-id",
			"dynamic reference marker ^ is not a valid MPS node ID",
			nil,
			"node-id",
			file,
		))
		return
	}
	if !supportedNodeID(id) {
		target.Findings = append(target.Findings, target.findingInFile(
			"error",
			"invalid-node-id",
			fmt.Sprintf("invalid MPS node ID %q", id),
			nil,
			"node-id",
			file,
		))
		return
	}
	if _, exists := seenNodeIDs[id]; exists {
		target.Findings = append(target.Findings, target.findingInFile(
			"error",
			"duplicate-node-id",
			fmt.Sprintf("duplicate MPS node ID %q", id),
			nil,
			"node-id",
			file,
		))
		return
	}
	seenNodeIDs[id] = struct{}{}
}

func attr(start xml.StartElement, name string) string {
	for _, attr := range start.Attr {
		if attr.Name.Local == name {
			return attr.Value
		}
	}
	return ""
}

func supportedNodeID(id string) bool {
	if len(id) > 0 && id[0] == '~' {
		return true
	}
	_, ok := nodeids.DecodeRegular(id)
	return ok
}

func (t TargetReport) malformedXMLFinding(err error, location *Location, file string) Finding {
	return t.findingInFile(
		"error",
		"malformed-xml",
		fmt.Sprintf("malformed XML: %v", err),
		location,
		"xml-parser",
		file,
	)
}

func (t TargetReport) finding(severity, code, message string, location *Location, source string) Finding {
	return t.findingInFile(severity, code, message, location, source, "")
}

func (t TargetReport) findingInFile(severity, code, message string, location *Location, source, file string) Finding {
	return Finding{
		Severity: severity,
		Code:     code,
		Message:  message,
		Target:   t.Target,
		File:     file,
		Location: location,
		Layer:    "structural",
		Source:   source,
	}
}

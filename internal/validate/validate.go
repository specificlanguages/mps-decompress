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
	data, err := os.ReadFile(path)
	if err != nil {
		target.Findings = append(target.Findings, target.finding("error", "target-read-failed", fmt.Sprintf("read target: %v", err), nil, "filesystem"))
		return target
	}

	persistenceVersion := ""
	seenNodeIDs := map[string]struct{}{}
	decoder := xml.NewDecoder(bytes.NewReader(data))
	depth := 0
	rootClosed := false
	for {
		token, err := decoder.Token()
		if err != nil {
			if err == io.EOF {
				break
			}
			line, column := decoder.InputPos()
			target.Findings = append(target.Findings, target.malformedXMLFinding(err, &Location{Line: line, Column: column}))
			return target
		}

		switch token := token.(type) {
		case xml.StartElement:
			if rootClosed {
				line, column := decoder.InputPos()
				target.Findings = append(target.Findings, target.malformedXMLFinding(
					fmt.Errorf("trailing content after document element"),
					&Location{Line: line, Column: column},
				))
				return target
			}
			depth++
			switch token.Name.Local {
			case "persistence":
				persistenceVersion = attr(token, "version")
			case "node":
				validateNodeID(&target, seenNodeIDs, attr(token, "id"))
			}
		case xml.EndElement:
			if depth > 0 {
				depth--
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
				))
				return target
			}
		}
	}

	if persistenceVersion != "9" {
		target.Findings = append(target.Findings, target.finding(
			"error",
			"unsupported-persistence-version",
			fmt.Sprintf("unsupported persistence version %q", persistenceVersion),
			nil,
			"persistence",
		))
	}

	return target
}

func validateNodeID(target *TargetReport, seenNodeIDs map[string]struct{}, id string) {
	if id == "^" {
		target.Findings = append(target.Findings, target.finding(
			"error",
			"forbidden-dynamic-node-id",
			"dynamic reference marker ^ is not a valid MPS node ID",
			nil,
			"node-id",
		))
		return
	}
	if !supportedNodeID(id) {
		target.Findings = append(target.Findings, target.finding(
			"error",
			"invalid-node-id",
			fmt.Sprintf("invalid MPS node ID %q", id),
			nil,
			"node-id",
		))
		return
	}
	if _, exists := seenNodeIDs[id]; exists {
		target.Findings = append(target.Findings, target.finding(
			"error",
			"duplicate-node-id",
			fmt.Sprintf("duplicate MPS node ID %q", id),
			nil,
			"node-id",
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

func (t TargetReport) malformedXMLFinding(err error, location *Location) Finding {
	return t.finding(
		"error",
		"malformed-xml",
		fmt.Sprintf("malformed XML: %v", err),
		location,
		"xml-parser",
	)
}

func (t TargetReport) finding(severity, code, message string, location *Location, source string) Finding {
	return Finding{
		Severity: severity,
		Code:     code,
		Message:  message,
		Target:   t.Target,
		Location: location,
		Layer:    "structural",
		Source:   source,
	}
}

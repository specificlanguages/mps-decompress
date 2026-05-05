package validate

import (
	"bytes"
	"encoding/json"
	"encoding/xml"
	"fmt"
	"io"
	"os"
	"path/filepath"
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

	var model struct {
		Persistence struct {
			Version string `xml:"version,attr"`
		} `xml:"persistence"`
	}
	decoder := xml.NewDecoder(bytes.NewReader(data))
	if err := decoder.Decode(&model); err != nil {
		line, column := decoder.InputPos()
		target.Findings = append(target.Findings, target.malformedXMLFinding(err, &Location{Line: line, Column: column}))
		return target
	}
	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		line, column := decoder.InputPos()
		if err == nil {
			err = fmt.Errorf("trailing content after document element")
		}
		target.Findings = append(target.Findings, target.malformedXMLFinding(err, &Location{Line: line, Column: column}))
		return target
	}

	if model.Persistence.Version != "9" {
		target.Findings = append(target.Findings, target.finding(
			"error",
			"unsupported-persistence-version",
			fmt.Sprintf("unsupported persistence version %q", model.Persistence.Version),
			nil,
			"persistence",
		))
	}

	return target
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

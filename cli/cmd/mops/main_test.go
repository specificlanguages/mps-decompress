package main

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
)

func TestRunPrintsVersion(t *testing.T) {
	var stdout, stderr bytes.Buffer

	exit := run([]string{"--version"}, strings.NewReader(""), &stdout, &stderr)

	if exit != 0 {
		t.Fatalf("exit = %d, want 0", exit)
	}
	if got, want := stdout.String(), "mops 0.2.0\n"; got != want {
		t.Fatalf("stdout = %q, want %q", got, want)
	}
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestRunRequiresCommand(t *testing.T) {
	var stdout, stderr bytes.Buffer

	exit := run(nil, strings.NewReader(""), &stdout, &stderr)

	if exit != 2 {
		t.Fatalf("exit = %d, want 2", exit)
	}
	assertContains(t, stderr.String(), "expected command")
	assertContains(t, stderr.String(), "Usage: mops")
	if stdout.Len() != 0 {
		t.Fatalf("stdout = %q, want empty", stdout.String())
	}
}

func TestRunRejectsUnknownCommand(t *testing.T) {
	var stdout, stderr bytes.Buffer

	exit := run([]string{"foo"}, strings.NewReader(""), &stdout, &stderr)

	if exit != 2 {
		t.Fatalf("exit = %d, want 2", exit)
	}
	assertContains(t, stderr.String(), "unknown command: foo")
	assertContains(t, stderr.String(), "Usage: mops")
	if stdout.Len() != 0 {
		t.Fatalf("stdout = %q, want empty", stdout.String())
	}
}

func TestRunPrintsExpandHelp(t *testing.T) {
	var stdout, stderr bytes.Buffer

	exit := run([]string{"expand", "--help"}, strings.NewReader(""), &stdout, &stderr)

	if exit != 0 {
		t.Fatalf("exit = %d, want 0", exit)
	}
	assertContains(t, stdout.String(), "Usage: mops expand [input.mps]")
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestRunPrintsListModelsHelp(t *testing.T) {
	var stdout, stderr bytes.Buffer

	exit := run([]string{"list-models", "--help"}, strings.NewReader(""), &stdout, &stderr)

	if exit != 0 {
		t.Fatalf("exit = %d, want 0", exit)
	}
	assertContains(t, stdout.String(), "Usage: mops list-models [root]")
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestRunPrintsGenerateIDsHelp(t *testing.T) {
	var stdout, stderr bytes.Buffer

	exit := run([]string{"generate-ids", "--help"}, strings.NewReader(""), &stdout, &stderr)

	if exit != 0 {
		t.Fatalf("exit = %d, want 0", exit)
	}
	assertContains(t, stdout.String(), "Usage: mops generate-ids [--long] <model.mps|model-folder> <count>")
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestRunExpandsFromStdin(t *testing.T) {
	input := `<?xml version="1.0" encoding="UTF-8"?>
<model ref="r:sample">
  <persistence version="9" />
  <registry>
    <language id="123" name="example.lang">
      <concept index="1" name="example.lang.Root">
        <child index="2" name="children" />
        <property index="3" name="title" />
        <reference index="4" name="target" />
      </concept>
    </language>
  </registry>
  <node concept="1" id="1">
    <property role="3" value="hello" />
  </node>
</model>`
	var stdout, stderr bytes.Buffer

	exit := run([]string{"expand"}, strings.NewReader(input), &stdout, &stderr)

	if exit != 0 {
		t.Fatalf("exit = %d, want 0; stderr = %q", exit, stderr.String())
	}
	assertContains(t, stdout.String(), `<node concept="example.lang.Root" id="1">`)
	assertContains(t, stdout.String(), `<property role="title" value="hello" />`)
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestRunGenerateIDsPrintsShortIDs(t *testing.T) {
	root := t.TempDir()
	modelPath := filepath.Join(root, "model.mps")
	if err := os.WriteFile(modelPath, []byte(`<model />`), 0o644); err != nil {
		t.Fatalf("WriteFile() error = %v", err)
	}

	var stdout, stderr bytes.Buffer

	exit := run([]string{"generate-ids", modelPath, "3"}, strings.NewReader(""), &stdout, &stderr)

	if exit != 0 {
		t.Fatalf("exit = %d, want 0; stderr = %q", exit, stderr.String())
	}
	lines := nonEmptyLines(stdout.String())
	if len(lines) != 3 {
		t.Fatalf("lines = %#v, want 3 lines", lines)
	}
	for _, line := range lines {
		if strings.Contains(line, "-") {
			t.Fatalf("short ID %q contains decimal minus sign", line)
		}
	}
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestRunGenerateIDsPrintsExpandedIDs(t *testing.T) {
	root := t.TempDir()
	modelPath := filepath.Join(root, "model.mps")
	if err := os.WriteFile(modelPath, []byte(`<model />`), 0o644); err != nil {
		t.Fatalf("WriteFile() error = %v", err)
	}

	var stdout, stderr bytes.Buffer

	exit := run([]string{"generate-ids", "--long", modelPath, "2"}, strings.NewReader(""), &stdout, &stderr)

	if exit != 0 {
		t.Fatalf("exit = %d, want 0; stderr = %q", exit, stderr.String())
	}
	lines := nonEmptyLines(stdout.String())
	if len(lines) != 2 {
		t.Fatalf("lines = %#v, want 2 lines", lines)
	}
	for _, line := range lines {
		if _, err := strconv.ParseInt(line, 10, 64); err != nil {
			t.Fatalf("ParseInt(%q) error = %v", line, err)
		}
	}
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestRunGenerateIDsRejectsInvalidCount(t *testing.T) {
	var stdout, stderr bytes.Buffer

	exit := run([]string{"generate-ids", "model.mps", "-1"}, strings.NewReader(""), &stdout, &stderr)

	if exit != 2 {
		t.Fatalf("exit = %d, want 2", exit)
	}
	assertContains(t, stderr.String(), "count must be a non-negative integer")
	if stdout.Len() != 0 {
		t.Fatalf("stdout = %q, want empty", stdout.String())
	}
}

func TestRunValidateAcceptsStandalonePersistenceVersion9(t *testing.T) {
	root := t.TempDir()
	modelPath := filepath.Join(root, "sample.mps")
	writeFile(t, modelPath, `<model ref="r:sample"><persistence version="9" /></model>`)

	var stdout, stderr bytes.Buffer

	exit := run([]string{"validate", modelPath}, strings.NewReader(""), &stdout, &stderr)

	if exit != 0 {
		t.Fatalf("exit = %d, want 0; stderr = %q", exit, stderr.String())
	}
	assertContains(t, stdout.String(), "OK")
	assertContains(t, stdout.String(), filepath.ToSlash(modelPath))
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestRunValidateJSONReportsMalformedXML(t *testing.T) {
	root := t.TempDir()
	modelPath := filepath.Join(root, "broken.mps")
	writeFile(t, modelPath, `<model><persistence version="9"></model>`)

	var stdout, stderr bytes.Buffer

	exit := run([]string{"validate", "--json", modelPath}, strings.NewReader(""), &stdout, &stderr)

	if exit != 1 {
		t.Fatalf("exit = %d, want 1; stderr = %q", exit, stderr.String())
	}
	var report struct {
		Status   string `json:"status"`
		Findings []struct {
			Severity string `json:"severity"`
			Code     string `json:"code"`
			Message  string `json:"message"`
			Target   string `json:"target"`
			Location struct {
				Line   int `json:"line"`
				Column int `json:"column"`
			} `json:"location"`
			Layer  string `json:"layer"`
			Source string `json:"source"`
		} `json:"findings"`
	}
	if err := json.Unmarshal(stdout.Bytes(), &report); err != nil {
		t.Fatalf("Unmarshal() error = %v; stdout = %q", err, stdout.String())
	}
	if report.Status != "failed" {
		t.Fatalf("status = %q, want failed", report.Status)
	}
	if len(report.Findings) != 1 {
		t.Fatalf("findings = %#v, want one finding", report.Findings)
	}
	finding := report.Findings[0]
	if finding.Severity != "error" {
		t.Fatalf("severity = %q, want error", finding.Severity)
	}
	if finding.Code != "malformed-xml" {
		t.Fatalf("code = %q, want malformed-xml", finding.Code)
	}
	assertContains(t, finding.Message, "malformed XML")
	if finding.Target != filepath.ToSlash(modelPath) {
		t.Fatalf("target = %q, want %q", finding.Target, filepath.ToSlash(modelPath))
	}
	if finding.Location.Line == 0 || finding.Location.Column == 0 {
		t.Fatalf("location = %#v, want line and column", finding.Location)
	}
	if finding.Layer != "structural" {
		t.Fatalf("layer = %q, want structural", finding.Layer)
	}
	if finding.Source != "xml-parser" {
		t.Fatalf("source = %q, want xml-parser", finding.Source)
	}
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestRunValidateJSONReportsSuccess(t *testing.T) {
	root := t.TempDir()
	modelPath := filepath.Join(root, "sample.mps")
	writeFile(t, modelPath, `<model ref="r:sample"><persistence version="9" /></model>`)

	var stdout, stderr bytes.Buffer

	exit := run([]string{"validate", "--json", modelPath}, strings.NewReader(""), &stdout, &stderr)

	if exit != 0 {
		t.Fatalf("exit = %d, want 0; stderr = %q", exit, stderr.String())
	}
	var report struct {
		Status   string `json:"status"`
		Findings []any  `json:"findings"`
	}
	if err := json.Unmarshal(stdout.Bytes(), &report); err != nil {
		t.Fatalf("Unmarshal() error = %v; stdout = %q", err, stdout.String())
	}
	if report.Status != "ok" {
		t.Fatalf("status = %q, want ok", report.Status)
	}
	if len(report.Findings) != 0 {
		t.Fatalf("findings = %#v, want empty", report.Findings)
	}
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestRunValidateStandaloneRootFileExitsZeroForIncompleteValidation(t *testing.T) {
	root := t.TempDir()
	rootPath := filepath.Join(root, "sample.mpsr")
	writeFile(t, rootPath, `<model ref="r:sample" content="root">
  <persistence version="9" />
  <node concept="1" id="1" />
</model>`)

	var stdout, stderr bytes.Buffer

	exit := run([]string{"validate", "--json", rootPath}, strings.NewReader(""), &stdout, &stderr)

	if exit != 0 {
		t.Fatalf("exit = %d, want 0; stderr = %q; stdout = %q", exit, stderr.String(), stdout.String())
	}
	assertContains(t, stdout.String(), `"status": "ok"`)
	assertContains(t, stdout.String(), `"code": "incomplete-validation"`)
	assertContains(t, stdout.String(), "for complete structural validation")
	assertContains(t, stdout.String(), "mops validate "+filepath.ToSlash(root))
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestRunValidateHumanOutputReportsRootFileFindingLocation(t *testing.T) {
	root := t.TempDir()
	modelFolder := filepath.Join(root, "models", "sample")
	brokenRoot := filepath.Join(modelFolder, "broken.mpsr")
	writeFile(t, filepath.Join(modelFolder, ".model"), `<model ref="r:sample"><persistence version="9" /></model>`)
	writeFile(t, brokenRoot, `<model ref="r:sample" content="root">
  <persistence version="9" />
  <node concept="1" id="not-supported" />
</model>`)

	var stdout, stderr bytes.Buffer

	exit := run([]string{"validate", modelFolder}, strings.NewReader(""), &stdout, &stderr)

	if exit != 1 {
		t.Fatalf("exit = %d, want 1; stderr = %q; stdout = %q", exit, stderr.String(), stdout.String())
	}
	assertContains(t, stdout.String(), filepath.ToSlash(brokenRoot))
	assertContains(t, stdout.String(), "invalid MPS node ID")
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestRunValidateChecksEveryTargetBeforeFailing(t *testing.T) {
	root := t.TempDir()
	validPath := filepath.Join(root, "valid.mps")
	unsupportedPath := filepath.Join(root, "unsupported.mps")
	writeFile(t, validPath, `<model ref="r:valid"><persistence version="9" /></model>`)
	writeFile(t, unsupportedPath, `<model ref="r:unsupported"><persistence version="8" /></model>`)

	var stdout, stderr bytes.Buffer

	exit := run([]string{"validate", validPath, unsupportedPath}, strings.NewReader(""), &stdout, &stderr)

	if exit != 1 {
		t.Fatalf("exit = %d, want 1; stderr = %q", exit, stderr.String())
	}
	assertContains(t, stdout.String(), "OK "+filepath.ToSlash(validPath))
	assertContains(t, stdout.String(), "unsupported persistence version")
	assertContains(t, stdout.String(), filepath.ToSlash(unsupportedPath))
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestRunValidateRequiresExplicitTarget(t *testing.T) {
	var stdout, stderr bytes.Buffer

	exit := run([]string{"validate"}, strings.NewReader(""), &stdout, &stderr)

	if exit != 2 {
		t.Fatalf("exit = %d, want 2", exit)
	}
	assertContains(t, stderr.String(), "expected at least one validation target")
	if stdout.Len() != 0 {
		t.Fatalf("stdout = %q, want empty", stdout.String())
	}
}

func TestRunListModelsPrintsJSON(t *testing.T) {
	root := t.TempDir()
	modelPath := filepath.Join(root, "sample.mps")
	if err := os.WriteFile(modelPath, []byte(`<model ref="r:sample" />`), 0o644); err != nil {
		t.Fatalf("WriteFile() error = %v", err)
	}

	var stdout, stderr bytes.Buffer

	exit := run([]string{"list-models", root}, strings.NewReader(""), &stdout, &stderr)

	if exit != 0 {
		t.Fatalf("exit = %d, want 0; stderr = %q", exit, stderr.String())
	}
	var got map[string]any
	if err := json.Unmarshal(stdout.Bytes(), &got); err != nil {
		t.Fatalf("Unmarshal() error = %v; stdout = %q", err, stdout.String())
	}
	if got["r:sample"] != filepath.ToSlash(modelPath) {
		t.Fatalf("r:sample = %#v, want %q", got["r:sample"], filepath.ToSlash(modelPath))
	}
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func writeFile(t *testing.T, path string, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatalf("MkdirAll() error = %v", err)
	}
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatalf("WriteFile() error = %v", err)
	}
}

func assertContains(t *testing.T, s, substr string) {
	t.Helper()
	if !strings.Contains(s, substr) {
		t.Fatalf("%q does not contain %q", s, substr)
	}
}

func nonEmptyLines(s string) []string {
	var lines []string
	for _, line := range strings.Split(s, "\n") {
		if line != "" {
			lines = append(lines, line)
		}
	}
	return lines
}

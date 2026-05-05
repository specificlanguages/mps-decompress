package validate

import (
	"os"
	"path/filepath"
	"testing"
)

func TestRunRejectsMalformedTrailingXMLAfterModelRoot(t *testing.T) {
	root := t.TempDir()
	modelPath := filepath.Join(root, "broken.mps")
	if err := os.WriteFile(modelPath, []byte(`<model><persistence version="9"/></model><broken`), 0o644); err != nil {
		t.Fatalf("WriteFile() error = %v", err)
	}

	report := Run([]string{modelPath})

	if report.Status != "failed" {
		t.Fatalf("status = %q, want failed", report.Status)
	}
	if len(report.Findings) != 1 {
		t.Fatalf("findings = %#v, want one finding", report.Findings)
	}
	finding := report.Findings[0]
	if finding.Code != "malformed-xml" {
		t.Fatalf("code = %q, want malformed-xml", finding.Code)
	}
	if finding.Location == nil || finding.Location.Line == 0 || finding.Location.Column == 0 {
		t.Fatalf("location = %#v, want line and column", finding.Location)
	}
}

func TestRunReportsInvalidRegularNodeID(t *testing.T) {
	tests := []struct {
		name string
		id   string
	}{
		{name: "leading zero regular ID", id: "0001"},
		{name: "custom ID family", id: "custom-id"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			modelPath := writeModel(t, `<model ref="r:sample">
  <persistence version="9" />
  <node concept="1" id="`+tt.id+`" />
</model>`)

			report := Run([]string{modelPath})

			assertFinding(t, report, "error", "invalid-node-id", filepath.ToSlash(modelPath))
		})
	}
}

func TestRunAcceptsSupportedNodeIDs(t *testing.T) {
	modelPath := writeModel(t, `<model ref="r:sample">
  <persistence version="9" />
  <node concept="1" id="0" />
  <node concept="1" id="1V" />
  <node concept="1" id="~" />
  <node concept="1" id="~foreign" />
</model>`)

	report := Run([]string{modelPath})

	if report.HasErrors() {
		t.Fatalf("HasErrors() = true, findings = %#v", report.Findings)
	}
}

func TestRunReportsForbiddenDynamicNodeIDMarker(t *testing.T) {
	modelPath := writeModel(t, `<model ref="r:sample">
  <persistence version="9" />
  <node concept="1" id="^" />
</model>`)

	report := Run([]string{modelPath})

	assertFinding(t, report, "error", "forbidden-dynamic-node-id", filepath.ToSlash(modelPath))
}

func TestRunReportsDuplicateNodeIDsInStandaloneTarget(t *testing.T) {
	modelPath := writeModel(t, `<model ref="r:sample">
  <persistence version="9" />
  <node concept="1" id="1">
    <node concept="2" id="2" role="3" />
  </node>
  <node concept="1" id="2" />
</model>`)

	report := Run([]string{modelPath})

	assertFinding(t, report, "error", "duplicate-node-id", filepath.ToSlash(modelPath))
}

func writeModel(t *testing.T, content string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "sample.mps")
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatalf("WriteFile() error = %v", err)
	}
	return path
}

func assertFinding(t *testing.T, report Report, severity, code, target string) {
	t.Helper()
	for _, finding := range report.Findings {
		if finding.Severity == severity && finding.Code == code && finding.Target == target {
			return
		}
	}
	t.Fatalf("finding (%s, %s, %s) not found in %#v", severity, code, target, report.Findings)
}

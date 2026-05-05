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

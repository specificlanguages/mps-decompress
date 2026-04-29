package main

import (
	"bytes"
	"strings"
	"testing"
)

func TestRunPrintsVersion(t *testing.T) {
	var stdout, stderr bytes.Buffer

	exit := run([]string{"--version"}, strings.NewReader(""), &stdout, &stderr)

	if exit != 0 {
		t.Fatalf("exit = %d, want 0", exit)
	}
	if got, want := stdout.String(), "mops 0.1.0\n"; got != want {
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

func TestRunPrintsDecompressHelp(t *testing.T) {
	var stdout, stderr bytes.Buffer

	exit := run([]string{"decompress", "--help"}, strings.NewReader(""), &stdout, &stderr)

	if exit != 0 {
		t.Fatalf("exit = %d, want 0", exit)
	}
	assertContains(t, stdout.String(), "Usage: mops decompress [input.mps]")
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestRunDecompressesFromStdin(t *testing.T) {
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

	exit := run([]string{"decompress"}, strings.NewReader(input), &stdout, &stderr)

	if exit != 0 {
		t.Fatalf("exit = %d, want 0; stderr = %q", exit, stderr.String())
	}
	assertContains(t, stdout.String(), `<node concept="example.lang.Root" id="1">`)
	assertContains(t, stdout.String(), `<property role="title" value="hello" />`)
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func assertContains(t *testing.T, s, substr string) {
	t.Helper()
	if !strings.Contains(s, substr) {
		t.Fatalf("%q does not contain %q", s, substr)
	}
}

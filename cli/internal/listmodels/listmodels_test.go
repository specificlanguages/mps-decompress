package listmodels

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

func TestFindReportsMPSFileLocation(t *testing.T) {
	root := t.TempDir()
	modelPath := filepath.Join(root, "models", "sample.mps")
	writeFile(t, modelPath, `<model ref="r:sample"><persistence version="9" /></model>`)

	got, err := Find(root)
	if err != nil {
		t.Fatalf("Find() error = %v", err)
	}

	want := LocationMap{
		"r:sample": {slashAbs(t, modelPath)},
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("Find() = %#v, want %#v", got, want)
	}
}

func TestFindReportsFilePerRootFolderLocation(t *testing.T) {
	root := t.TempDir()
	modelPath := filepath.Join(root, "solution", "models", "sample", ".model")
	writeFile(t, modelPath, `<model ref="r:file-per-root"></model>`)

	got, err := Find(root)
	if err != nil {
		t.Fatalf("Find() error = %v", err)
	}

	want := LocationMap{
		"r:file-per-root": {slashAbs(t, filepath.Dir(modelPath)) + "/"},
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("Find() = %#v, want %#v", got, want)
	}
}

func TestFindSkipsNonModelsAndMalformedXML(t *testing.T) {
	root := t.TempDir()
	writeFile(t, filepath.Join(root, "not-model.mps"), `<notModel ref="r:nope" />`)
	writeFile(t, filepath.Join(root, "malformed.mps"), `<`)
	writeFile(t, filepath.Join(root, "missing-ref.mps"), `<model />`)
	writeFile(t, filepath.Join(root, "valid.mps"), `<model ref="r:valid" />`)

	got, err := Find(root)
	if err != nil {
		t.Fatalf("Find() error = %v", err)
	}

	if len(got) != 1 {
		t.Fatalf("Find() = %#v, want one valid model", got)
	}
	if _, ok := got["r:valid"]; !ok {
		t.Fatalf("Find() = %#v, want r:valid", got)
	}
}

func TestFindUsesArraysForMultipleLocations(t *testing.T) {
	root := t.TempDir()
	first := filepath.Join(root, "a.mps")
	second := filepath.Join(root, "nested", "b.mps")
	writeFile(t, first, `<model ref="r:duplicate" />`)
	writeFile(t, second, `<model ref="r:duplicate" />`)

	got, err := Find(root)
	if err != nil {
		t.Fatalf("Find() error = %v", err)
	}

	want := []string{slashAbs(t, first), slashAbs(t, second)}
	if !reflect.DeepEqual(got["r:duplicate"], want) {
		t.Fatalf("Find()[r:duplicate] = %#v, want %#v", got["r:duplicate"], want)
	}
}

func TestFindSkipsGitDirectoryDuringFallbackWalk(t *testing.T) {
	root := t.TempDir()
	writeFile(t, filepath.Join(root, ".git", "ignored.mps"), `<model ref="r:git" />`)
	writeFile(t, filepath.Join(root, "real.mps"), `<model ref="r:real" />`)

	got, err := Find(root)
	if err != nil {
		t.Fatalf("Find() error = %v", err)
	}

	if _, ok := got["r:git"]; ok {
		t.Fatalf("Find() = %#v, did not expect .git model", got)
	}
	if _, ok := got["r:real"]; !ok {
		t.Fatalf("Find() = %#v, want r:real", got)
	}
}

func writeFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatalf("MkdirAll() error = %v", err)
	}
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatalf("WriteFile() error = %v", err)
	}
}

func slashAbs(t *testing.T, path string) string {
	t.Helper()
	abs, err := filepath.Abs(path)
	if err != nil {
		t.Fatalf("Abs() error = %v", err)
	}
	return filepath.ToSlash(abs)
}

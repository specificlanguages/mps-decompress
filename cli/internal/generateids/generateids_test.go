package generateids

import (
	"errors"
	"math"
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

func TestGenerateScansSingleMPSFile(t *testing.T) {
	root := t.TempDir()
	model := filepath.Join(root, "model.mps")
	writeFile(t, model, `<model><node id="b" /><node id="c" /></model>`)

	got, err := Generate(model, 4, fixedRand(10))
	if err != nil {
		t.Fatalf("Generate() error = %v", err)
	}

	want := []int64{13, 14, 15, 16}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("Generate() = %#v, want %#v", got, want)
	}
}

func TestGenerateScansMPSRFilesInFolder(t *testing.T) {
	root := t.TempDir()
	writeFile(t, filepath.Join(root, "b.mpsr"), `<node id="6" />`)
	writeFile(t, filepath.Join(root, "a.mpsr"), `<node id="5" />`)
	writeFile(t, filepath.Join(root, ".model"), `<model ref="r:sample" />`)
	writeFile(t, filepath.Join(root, "nested", "ignored.mpsr"), `<node id="7" />`)

	got, err := Generate(root, 3, fixedRand(4))
	if err != nil {
		t.Fatalf("Generate() error = %v", err)
	}

	want := []int64{7, 8, 9}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("Generate() = %#v, want %#v", got, want)
	}
}

func TestGenerateAllowsFolderWithoutMPSRFiles(t *testing.T) {
	root := t.TempDir()

	got, err := Generate(root, 2, fixedRand(100))
	if err != nil {
		t.Fatalf("Generate() error = %v", err)
	}

	want := []int64{101, 102}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("Generate() = %#v, want %#v", got, want)
	}
}

func TestGenerateFailsOnMalformedXML(t *testing.T) {
	root := t.TempDir()
	model := filepath.Join(root, "model.mps")
	writeFile(t, model, `<model><node id="1">`)

	_, err := Generate(model, 1, fixedRand(1))

	if err == nil {
		t.Fatal("Generate() error = nil, want error")
	}
}

func TestGenerateIgnoresForeignAndMalformedNodeIDs(t *testing.T) {
	root := t.TempDir()
	model := filepath.Join(root, "model.mps")
	writeFile(t, model, `<model><node id="~foreign" /><node id="0001" /><node id="^" /></model>`)

	got, err := Generate(model, 2, fixedRand(0))
	if err != nil {
		t.Fatalf("Generate() error = %v", err)
	}

	want := []int64{1, 2}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("Generate() = %#v, want %#v", got, want)
	}
}

func TestGenerateHandlesNegativeRandomStartLikeMPS(t *testing.T) {
	root := t.TempDir()
	model := filepath.Join(root, "model.mps")
	writeFile(t, model, `<model />`)

	got, err := Generate(model, 2, fixedRand(-10))
	if err != nil {
		t.Fatalf("Generate() error = %v", err)
	}

	want := []int64{11, 12}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("Generate() = %#v, want %#v", got, want)
	}
}

func TestGenerateAvoidsMinInt64(t *testing.T) {
	root := t.TempDir()
	model := filepath.Join(root, "model.mps")
	writeFile(t, model, `<model />`)

	got, err := Generate(model, 1, fixedRand(math.MinInt64))
	if err != nil {
		t.Fatalf("Generate() error = %v", err)
	}

	want := []int64{math.MaxInt64}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("Generate() = %#v, want %#v", got, want)
	}
}

func TestGeneratePropagatesRandomError(t *testing.T) {
	root := t.TempDir()
	model := filepath.Join(root, "model.mps")
	writeFile(t, model, `<model />`)
	want := errors.New("boom")

	_, err := Generate(model, 1, func() (int64, error) {
		return 0, want
	})

	if !errors.Is(err, want) {
		t.Fatalf("Generate() error = %v, want %v", err, want)
	}
}

func TestEncoded(t *testing.T) {
	got := Encoded([]int64{1, 64, 123})
	want := []string{"1", "10", "1V"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("Encoded() = %#v, want %#v", got, want)
	}
}

func fixedRand(value int64) RandInt64 {
	return func() (int64, error) {
		return value, nil
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

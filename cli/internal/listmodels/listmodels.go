package listmodels

import (
	"encoding/xml"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
)

type LocationMap map[string][]string

func Find(root string) (LocationMap, error) {
	absRoot, err := filepath.Abs(root)
	if err != nil {
		return nil, err
	}

	info, err := os.Stat(absRoot)
	if err != nil {
		return nil, err
	}
	if !info.IsDir() {
		return nil, &notDirError{path: absRoot}
	}

	files, ok := gitFiles(absRoot)
	if !ok {
		files, err = walkFiles(absRoot)
		if err != nil {
			return nil, err
		}
	}

	locations := make(map[string]map[string]struct{})
	for _, path := range files {
		if !isCandidate(path) {
			continue
		}

		ref, ok := modelRef(path)
		if !ok {
			continue
		}

		location, err := locationFor(path)
		if err != nil {
			continue
		}
		if locations[ref] == nil {
			locations[ref] = make(map[string]struct{})
		}
		locations[ref][location] = struct{}{}
	}

	result := make(LocationMap, len(locations))
	for ref, paths := range locations {
		result[ref] = sortedKeys(paths)
	}
	return result, nil
}

func gitFiles(root string) ([]string, bool) {
	cmd := exec.Command("git", "ls-files", "-co", "--exclude-standard", "-z")
	cmd.Dir = root
	output, err := cmd.Output()
	if err != nil {
		return nil, false
	}

	parts := strings.Split(string(output), "\x00")
	files := make([]string, 0, len(parts))
	for _, part := range parts {
		if part == "" {
			continue
		}
		files = append(files, filepath.Join(root, filepath.FromSlash(part)))
	}
	return files, true
}

func walkFiles(root string) ([]string, error) {
	var files []string
	err := filepath.WalkDir(root, func(path string, d os.DirEntry, err error) error {
		if err != nil {
			if path == root {
				return err
			}
			return nil
		}
		if d.IsDir() {
			if d.Name() == ".git" {
				return filepath.SkipDir
			}
			return nil
		}
		if isCandidate(path) {
			files = append(files, path)
		}
		return nil
	})
	return files, err
}

func isCandidate(path string) bool {
	name := filepath.Base(path)
	return name == ".model" || filepath.Ext(name) == ".mps"
}

func modelRef(path string) (string, bool) {
	file, err := os.Open(path)
	if err != nil {
		return "", false
	}
	defer file.Close()

	decoder := xml.NewDecoder(file)
	for {
		token, err := decoder.Token()
		if err != nil {
			if err == io.EOF {
				return "", false
			}
			return "", false
		}

		start, ok := token.(xml.StartElement)
		if !ok {
			continue
		}
		if start.Name.Local != "model" {
			return "", false
		}
		for _, attr := range start.Attr {
			if attr.Name.Local == "ref" && attr.Value != "" {
				return attr.Value, true
			}
		}
		return "", false
	}
}

func locationFor(path string) (string, error) {
	absPath, err := filepath.Abs(path)
	if err != nil {
		return "", err
	}
	if filepath.Base(absPath) == ".model" {
		return ensureTrailingSlash(filepath.ToSlash(filepath.Dir(absPath))), nil
	}
	return filepath.ToSlash(absPath), nil
}

func ensureTrailingSlash(path string) string {
	if strings.HasSuffix(path, "/") {
		return path
	}
	return path + "/"
}

func sortedKeys(paths map[string]struct{}) []string {
	keys := make([]string, 0, len(paths))
	for path := range paths {
		keys = append(keys, path)
	}
	sort.Strings(keys)
	return keys
}

type notDirError struct {
	path string
}

func (e *notDirError) Error() string {
	return "root is not a directory: " + e.path
}

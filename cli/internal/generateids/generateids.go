package generateids

import (
	"crypto/rand"
	"encoding/binary"
	"encoding/xml"
	"fmt"
	"io"
	"math"
	"os"
	"path/filepath"
	"sort"

	"mops/internal/nodeids"
)

type RandInt64 func() (int64, error)

func Generate(path string, count int, randInt64 RandInt64) ([]int64, error) {
	if count < 0 {
		return nil, fmt.Errorf("count must be non-negative")
	}
	if randInt64 == nil {
		randInt64 = CryptoRandInt64
	}

	files, err := scanFiles(path)
	if err != nil {
		return nil, err
	}

	used := map[int64]struct{}{}
	for _, file := range files {
		if err := collectNodeIDs(file, used); err != nil {
			return nil, err
		}
	}
	if count == 0 {
		return []int64{}, nil
	}

	start, err := randInt64()
	if err != nil {
		return nil, fmt.Errorf("generate random start: %w", err)
	}

	ids := make([]int64, 0, count)
	counter := initialCounter(start)
	for len(ids) < count {
		id, ok := nextMPSID(&counter)
		if !ok {
			continue
		}
		if _, ok := used[id]; ok {
			continue
		}
		ids = append(ids, id)
		used[id] = struct{}{}
	}
	return ids, nil
}

func CryptoRandInt64() (int64, error) {
	var buf [8]byte
	if _, err := io.ReadFull(rand.Reader, buf[:]); err != nil {
		return 0, err
	}
	return int64(binary.BigEndian.Uint64(buf[:])), nil
}

func Encoded(ids []int64) []string {
	result := make([]string, len(ids))
	for i, id := range ids {
		result[i] = nodeids.EncodeRegular(id)
	}
	return result
}

func scanFiles(path string) ([]string, error) {
	info, err := os.Stat(path)
	if err != nil {
		return nil, err
	}

	if !info.IsDir() {
		return []string{path}, nil
	}

	entries, err := os.ReadDir(path)
	if err != nil {
		return nil, err
	}

	var files []string
	for _, entry := range entries {
		if entry.IsDir() || filepath.Ext(entry.Name()) != ".mpsr" {
			continue
		}
		files = append(files, filepath.Join(path, entry.Name()))
	}
	sort.Strings(files)
	return files, nil
}

func collectNodeIDs(path string, used map[int64]struct{}) error {
	file, err := os.Open(path)
	if err != nil {
		return fmt.Errorf("read %s: %w", path, err)
	}
	defer file.Close()

	decoder := xml.NewDecoder(file)
	for {
		token, err := decoder.Token()
		if err == io.EOF {
			return nil
		}
		if err != nil {
			return fmt.Errorf("parse %s: %w", path, err)
		}

		start, ok := token.(xml.StartElement)
		if !ok || start.Name.Local != "node" {
			continue
		}
		for _, attr := range start.Attr {
			if attr.Name.Local != "id" {
				continue
			}
			if id, ok := nodeids.DecodeRegular(attr.Value); ok {
				used[id] = struct{}{}
			}
			break
		}
	}
}

func nextMPSID(counter *int64) (int64, bool) {
	if *counter == math.MaxInt64 {
		*counter = math.MinInt64
	} else {
		(*counter)++
	}
	if *counter == math.MinInt64 {
		return 0, false
	}
	if *counter < 0 {
		return -*counter, true
	}
	return *counter, true
}

func initialCounter(value int64) int64 {
	if value == math.MinInt64 {
		return math.MaxInt64
	}
	if value < 0 {
		return -value
	}
	return value
}

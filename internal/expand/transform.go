package expand

import (
	"bytes"
	"encoding/xml"
	"fmt"
	"io"
	"strings"

	"mops/internal/nodeids"
)

type registryMaps struct {
	concepts   map[string]string
	children   map[string]string
	properties map[string]string
	references map[string]string
	imports    map[string]string
}

func Transform(r io.Reader, w io.Writer) error {
	input, err := io.ReadAll(r)
	if err != nil {
		return fmt.Errorf("read input: %w", err)
	}

	maps, err := parseRegistry(input)
	if err != nil {
		return err
	}

	if err := transformXML(input, w, maps); err != nil {
		return err
	}
	return nil
}

func parseRegistry(input []byte) (*registryMaps, error) {
	dec := xml.NewDecoder(bytes.NewReader(input))
	maps := &registryMaps{
		concepts:   map[string]string{},
		children:   map[string]string{},
		properties: map[string]string{},
		references: map[string]string{},
		imports:    map[string]string{},
	}

	var (
		registryDepth    int
		foundRegistry    bool
		persistence      string
		foundPersistence bool
		importsDepth     int
	)

	for {
		tok, err := dec.Token()
		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, fmt.Errorf("parse registry: %w", err)
		}

		switch t := tok.(type) {
		case xml.StartElement:
			name := t.Name.Local

			if name == "persistence" {
				foundPersistence = true
				persistence = attr(t, "version")
			}

			if importsDepth > 0 {
				importsDepth++
			} else if name == "imports" {
				importsDepth = 1
			}
			if importsDepth > 0 && name == "import" {
				if err := addImport(maps.imports, attr(t, "index"), attr(t, "ref"), dec); err != nil {
					return nil, err
				}
			}

			if registryDepth > 0 {
				registryDepth++
				if err := addRegistryEntry(maps, t, dec); err != nil {
					return nil, err
				}
			} else if name == "registry" {
				foundRegistry = true
				registryDepth = 1
			}

		case xml.EndElement:
			if registryDepth > 0 {
				registryDepth--
			}
			if importsDepth > 0 {
				importsDepth--
			}
		}
	}

	if !foundPersistence {
		return nil, fmt.Errorf("missing persistence section")
	}
	if persistence != "9" {
		return nil, fmt.Errorf("unsupported persistence version: %s", persistence)
	}
	if !foundRegistry {
		return nil, fmt.Errorf("missing registry section")
	}

	return maps, nil
}

func addRegistryEntry(maps *registryMaps, elem xml.StartElement, dec *xml.Decoder) error {
	index := attr(elem, "index")
	name := attr(elem, "name")
	if index == "" || name == "" {
		return nil
	}

	var target map[string]string
	switch elem.Name.Local {
	case "concept":
		target = maps.concepts
	case "child":
		target = maps.children
	case "property":
		target = maps.properties
	case "reference":
		target = maps.references
	default:
		return nil
	}

	return addMapping(target, index, name, elem.Name.Local+" index", dec)
}

func addImport(imports map[string]string, index, ref string, dec *xml.Decoder) error {
	if index == "" || ref == "" {
		return nil
	}
	return addMapping(imports, index, ref, "import index", dec)
}

func addMapping(target map[string]string, key, value, label string, dec *xml.Decoder) error {
	if _, ok := target[key]; ok {
		line, col := dec.InputPos()
		return fmt.Errorf("duplicate %s %q at line %d, column %d", label, key, line, col)
	}
	target[key] = value
	return nil
}

func transformXML(input []byte, w io.Writer, maps *registryMaps) error {
	dec := xml.NewDecoder(bytes.NewReader(input))
	enc := newPrettyXMLEncoder(w)

	var registryDepth int

	for {
		tok, err := dec.Token()
		if err == io.EOF {
			break
		}
		if err != nil {
			return fmt.Errorf("transform XML: %w", err)
		}

		switch t := tok.(type) {
		case xml.StartElement:
			if registryDepth > 0 {
				registryDepth++
			} else if t.Name.Local == "registry" {
				registryDepth = 1
			}

			if registryDepth == 0 {
				t = rewriteStartElement(t, maps)
			}

			if err := enc.Start(t); err != nil {
				return fmt.Errorf("write stdout: %w", err)
			}

		case xml.EndElement:
			if err := enc.End(t); err != nil {
				return fmt.Errorf("write stdout: %w", err)
			}
			if registryDepth > 0 {
				registryDepth--
			}

		case xml.CharData:
			if strings.TrimSpace(string(t)) == "" {
				continue
			}
			if err := enc.CharData(t); err != nil {
				return fmt.Errorf("write stdout: %w", err)
			}

		default:
			if err := enc.Token(tok); err != nil {
				return fmt.Errorf("write stdout: %w", err)
			}
		}
	}

	if err := enc.Flush(); err != nil {
		return fmt.Errorf("write stdout: %w", err)
	}
	return nil
}

func rewriteStartElement(elem xml.StartElement, maps *registryMaps) xml.StartElement {
	for i := range elem.Attr {
		value := elem.Attr[i].Value
		switch elem.Name.Local {
		case "node":
			switch elem.Attr[i].Name.Local {
			case "concept":
				value = lookupOrOriginal(maps.concepts, value)
			case "id":
				value = decodeOrOriginal(value)
			case "role":
				value = lookupOrOriginal(maps.children, value)
			}
		case "property":
			if elem.Attr[i].Name.Local == "role" {
				value = lookupOrOriginal(maps.properties, value)
			}
		case "ref":
			switch elem.Attr[i].Name.Local {
			case "role":
				value = lookupOrOriginal(maps.references, value)
			case "node":
				value = decodeOrOriginal(value)
			case "to":
				value = expandTarget(value, maps.imports)
			}
		}
		elem.Attr[i].Value = value
	}
	return elem
}

func expandTarget(target string, imports map[string]string) string {
	sep := strings.IndexByte(target, ':')
	if sep < 0 {
		return target
	}

	prefix := target[:sep]
	nodeID := target[sep+1:]

	if expanded, ok := imports[prefix]; ok && prefix != "" {
		prefix = expanded
	}

	return prefix + ":" + decodeOrOriginal(nodeID)
}

func lookupOrOriginal(m map[string]string, value string) string {
	if expanded, ok := m[value]; ok {
		return expanded
	}
	return value
}

func decodeOrOriginal(value string) string {
	if decoded, ok := nodeids.DecodeRegularString(value); ok {
		return decoded
	}
	return value
}

func attr(elem xml.StartElement, name string) string {
	for _, attr := range elem.Attr {
		if attr.Name.Local == name {
			return attr.Value
		}
	}
	return ""
}

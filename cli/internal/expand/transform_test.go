package expand

import (
	"bytes"
	"encoding/xml"
	"strings"
	"testing"
)

func TestTransformExpandsRegistryIndicesImportsAndNodeIDs(t *testing.T) {
	input := `<?xml version="1.0" encoding="UTF-8"?>
<model ref="r:example(example)">
  <persistence version="9" />
  <imports>
    <import index="imp1" ref="model:with:colon" />
  </imports>
  <registry>
    <language id="lang-id" name="example">
      <concept id="100" name="example.structure.Root" flags="ng" index="C1">
        <child id="101" name="childRole" index="ch1" />
        <property id="102" name="propRole" index="pr1" />
        <reference id="103" name="refRole" index="rf1" />
      </concept>
      <concept id="104" name="example.structure.Child" flags="ng" index="C2" />
    </language>
  </registry>
  <node concept="C1" id="1V">
    <property role="pr1" value="keep-me" />
    <ref role="rf1" node="10" resolve="localTarget" />
    <ref role="rf1" to="imp1:1V" resolve="externalTarget" />
    <ref role="rf1" to=":10" resolve="localTarget" />
    <ref role="rf1" to="imp1:~Foreign" resolve="foreignTarget" />
    <node concept="C2" id="0001" role="ch1" />
  </node>
</model>`

	output := transformString(t, input)
	mustBeXML(t, output)

	assertContains(t, output, `concept="example.structure.Root"`)
	assertContains(t, output, `concept="example.structure.Child"`)
	assertContains(t, output, `id="123"`)
	assertContains(t, output, `id="0001"`)
	assertContains(t, output, `role="childRole"`)
	assertContains(t, output, `role="propRole"`)
	assertContains(t, output, `role="refRole"`)
	assertContains(t, output, `node="64"`)
	assertContains(t, output, `to="model:with:colon:123"`)
	assertContains(t, output, `to=":64"`)
	assertContains(t, output, `to="model:with:colon:~Foreign"`)
	assertContains(t, output, `value="keep-me"`)
	assertContains(t, output, `<property role="propRole" value="keep-me" />`)
	assertContains(t, output, `<ref role="refRole" node="64" resolve="localTarget" />`)
	assertContains(t, output, `<node concept="example.structure.Child" id="0001" role="childRole" />`)
}

func TestTransformLeavesRegistryValuesUnchanged(t *testing.T) {
	input := `<model>
  <persistence version="9" />
  <registry>
    <language id="lang-id" name="example">
      <concept id="100" name="example.structure.Root" flags="ng" index="C1">
        <child id="101" name="childRole" index="ch1" />
      </concept>
    </language>
  </registry>
  <node concept="C1" id="1" />
</model>`

	output := transformString(t, input)
	assertContains(t, output, `index="C1"`)
	assertContains(t, output, `index="ch1"`)
	assertContains(t, output, `id="100"`)
	assertContains(t, output, `id="101"`)
}

func TestTransformLeavesUnknownMappingsUnchanged(t *testing.T) {
	input := `<model>
  <persistence version="9" />
  <registry>
    <language id="lang-id" name="example">
      <concept id="100" name="example.structure.Root" flags="ng" index="C1" />
    </language>
  </registry>
  <node concept="missingConcept" id="1" role="missingChild">
    <property role="missingProperty" value="x" />
    <ref role="missingReference" to="missingImport:1" />
  </node>
</model>`

	output := transformString(t, input)
	assertContains(t, output, `concept="missingConcept"`)
	assertContains(t, output, `role="missingChild"`)
	assertContains(t, output, `role="missingProperty"`)
	assertContains(t, output, `role="missingReference"`)
	assertContains(t, output, `to="missingImport:1"`)
}

func TestTransformAllowsMissingImports(t *testing.T) {
	input := `<model>
  <persistence version="9" />
  <registry>
    <language id="lang-id" name="example">
      <concept id="100" name="example.structure.Root" flags="ng" index="C1" />
    </language>
  </registry>
  <node concept="C1" id="1" />
</model>`

	output := transformString(t, input)
	assertContains(t, output, `concept="example.structure.Root"`)
}

func TestTransformUsesEmptyElementSyntax(t *testing.T) {
	input := `<model>
  <persistence version="9" />
  <registry>
    <language id="lang-id" name="example">
      <concept id="100" name="example.structure.Root" flags="ng" index="C1" />
    </language>
  </registry>
  <node concept="C1" id="1" />
</model>`

	output := transformString(t, input)
	assertContains(t, output, `<persistence version="9" />`)
	assertContains(t, output, `<concept id="100" name="example.structure.Root" flags="ng" index="C1" />`)
	assertContains(t, output, `<node concept="example.structure.Root" id="1" />`)
	if strings.Contains(output, `></persistence>`) || strings.Contains(output, `></node>`) {
		t.Fatalf("output uses expanded empty element syntax:\n%s", output)
	}
}

func TestTransformRejectsUnsupportedPersistenceVersion(t *testing.T) {
	input := `<model>
  <persistence version="8" />
  <registry />
</model>`

	var out bytes.Buffer
	err := Transform(strings.NewReader(input), &out)
	if err == nil || !strings.Contains(err.Error(), "unsupported persistence version: 8") {
		t.Fatalf("Transform error = %v, want unsupported persistence version", err)
	}
}

func TestTransformRejectsMissingRegistry(t *testing.T) {
	input := `<model>
  <persistence version="9" />
</model>`

	var out bytes.Buffer
	err := Transform(strings.NewReader(input), &out)
	if err == nil || !strings.Contains(err.Error(), "missing registry section") {
		t.Fatalf("Transform error = %v, want missing registry section", err)
	}
}

func TestTransformRejectsDuplicateRegistryIndex(t *testing.T) {
	input := `<model>
  <persistence version="9" />
  <registry>
    <language id="lang-id" name="example">
      <concept id="100" name="example.structure.Root" flags="ng" index="C1" />
      <concept id="101" name="example.structure.Other" flags="ng" index="C1" />
    </language>
  </registry>
</model>`

	var out bytes.Buffer
	err := Transform(strings.NewReader(input), &out)
	if err == nil || !strings.Contains(err.Error(), `duplicate concept index "C1"`) {
		t.Fatalf("Transform error = %v, want duplicate concept index", err)
	}
}

func TestTransformRejectsDuplicateImportIndex(t *testing.T) {
	input := `<model>
  <persistence version="9" />
  <imports>
    <import index="imp1" ref="model:a" />
    <import index="imp1" ref="model:b" />
  </imports>
  <registry />
</model>`

	var out bytes.Buffer
	err := Transform(strings.NewReader(input), &out)
	if err == nil || !strings.Contains(err.Error(), `duplicate import index "imp1"`) {
		t.Fatalf("Transform error = %v, want duplicate import index", err)
	}
}

func transformString(t *testing.T, input string) string {
	t.Helper()

	var out bytes.Buffer
	if err := Transform(strings.NewReader(input), &out); err != nil {
		t.Fatalf("Transform returned error: %v", err)
	}
	return out.String()
}

func mustBeXML(t *testing.T, output string) {
	t.Helper()

	var v any
	if err := xml.Unmarshal([]byte(output), &v); err != nil {
		t.Fatalf("output is not well-formed XML: %v\n%s", err, output)
	}
}

func assertContains(t *testing.T, output, want string) {
	t.Helper()

	if !strings.Contains(output, want) {
		t.Fatalf("output does not contain %q:\n%s", want, output)
	}
}

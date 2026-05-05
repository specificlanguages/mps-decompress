package xmlschema

import "testing"

func TestLinkedVersion(t *testing.T) {
	if LinkedVersion() == "" {
		t.Fatal("LinkedVersion() is empty")
	}
}

func TestValidateMPSPersistenceAcceptsRootModel(t *testing.T) {
	err := ValidateMPSPersistence([]byte(`<model ref="r:sample" content="root">
  <persistence version="9" />
  <languages>
    <devkit ref="r:sample-devkit" />
  </languages>
  <registry>
    <language id="sample-language" name="sample">
      <concept index="1" name="sample.Concept">
        <child index="2" name="child" />
        <property index="3" name="name" />
        <reference index="4" name="target" />
      </concept>
    </language>
  </registry>
  <node concept="1" id="1">
    <property role="3" value="name" />
    <ref role="4" node="1" />
    <node concept="1" id="2" role="2" />
  </node>
</model>`))

	if err != nil {
		t.Fatalf("ValidateMPSPersistence() error = %v", err)
	}
}

func TestValidateMPSPersistenceRejectsUnknownElement(t *testing.T) {
	err := ValidateMPSPersistence([]byte(`<model ref="r:sample">
  <persistence version="9" />
  <unexpected />
</model>`))

	if err == nil {
		t.Fatal("ValidateMPSPersistence() error = nil, want error")
	}
}

package xmlschema

import "testing"

func TestLinkedVersion(t *testing.T) {
	if LinkedVersion() == "" {
		t.Fatal("LinkedVersion() is empty")
	}
}

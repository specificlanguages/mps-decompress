package decompress

import "testing"

func TestDecodeRegularNodeID(t *testing.T) {
	tests := []struct {
		input string
		want  string
		ok    bool
	}{
		{input: "0", want: "0", ok: true},
		{input: "1", want: "1", ok: true},
		{input: "f", want: "15", ok: true},
		{input: "Z", want: "63", ok: true},
		{input: "10", want: "64", ok: true},
		{input: "1V", want: "123", ok: true},
		{input: "80000000000", want: "-9223372036854775808", ok: true},
		{input: "0001", ok: false},
		{input: "^", ok: false},
		{input: "~foreign", ok: false},
		{input: "not-a-node-id", ok: false},
	}

	for _, tt := range tests {
		got, ok := decodeRegularNodeID(tt.input)
		if ok != tt.ok {
			t.Fatalf("decodeRegularNodeID(%q) ok = %v, want %v", tt.input, ok, tt.ok)
		}
		if got != tt.want {
			t.Fatalf("decodeRegularNodeID(%q) = %q, want %q", tt.input, got, tt.want)
		}
	}
}

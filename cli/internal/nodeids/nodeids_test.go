package nodeids

import "testing"

func TestDecodeRegular(t *testing.T) {
	tests := []struct {
		input string
		want  int64
		ok    bool
	}{
		{input: "0", want: 0, ok: true},
		{input: "1", want: 1, ok: true},
		{input: "f", want: 15, ok: true},
		{input: "Z", want: 63, ok: true},
		{input: "10", want: 64, ok: true},
		{input: "1V", want: 123, ok: true},
		{input: "80000000000", want: -9223372036854775808, ok: true},
		{input: "0001", ok: false},
		{input: "^", ok: false},
		{input: "~foreign", ok: false},
		{input: "not-a-node-id", ok: false},
	}

	for _, tt := range tests {
		got, ok := DecodeRegular(tt.input)
		if ok != tt.ok {
			t.Fatalf("DecodeRegular(%q) ok = %v, want %v", tt.input, ok, tt.ok)
		}
		if got != tt.want {
			t.Fatalf("DecodeRegular(%q) = %d, want %d", tt.input, got, tt.want)
		}
	}
}

func TestDecodeRegularString(t *testing.T) {
	got, ok := DecodeRegularString("1V")
	if !ok {
		t.Fatal("DecodeRegularString() ok = false, want true")
	}
	if got != "123" {
		t.Fatalf("DecodeRegularString() = %q, want %q", got, "123")
	}
}

func TestEncodeRegular(t *testing.T) {
	tests := []struct {
		input int64
		want  string
	}{
		{input: 0, want: "0"},
		{input: 1, want: "1"},
		{input: 15, want: "f"},
		{input: 63, want: "Z"},
		{input: 64, want: "10"},
		{input: 123, want: "1V"},
		{input: -9223372036854775808, want: "80000000000"},
	}

	for _, tt := range tests {
		if got := EncodeRegular(tt.input); got != tt.want {
			t.Fatalf("EncodeRegular(%d) = %q, want %q", tt.input, got, tt.want)
		}
	}
}

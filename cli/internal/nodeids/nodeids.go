package nodeids

import "strconv"

// Reimplementation of JetBrains MPS JavaFriendlyBase64 as used by
// jetbrains.mps.smodel.persistence.def.v9.IdEncoder for regular node IDs.
const javaFriendlyAlphabet = "0123456789abcdefghijklmnopqrstuvwxyz$_ABCDEFGHIJKLMNOPQRSTUVWXYZ"

func DecodeRegular(text string) (int64, bool) {
	if text == "" || text == "^" || text[0] == '~' {
		return 0, false
	}

	var bits uint64
	for i := 0; i < len(text); i++ {
		value := javaFriendlyValue(text[i])
		if value < 0 {
			return 0, false
		}
		bits = (bits << 6) | uint64(value)
	}

	id := int64(bits)
	if EncodeRegular(id) != text {
		return 0, false
	}

	return id, true
}

func DecodeRegularString(text string) (string, bool) {
	id, ok := DecodeRegular(text)
	if !ok {
		return "", false
	}
	return strconv.FormatInt(id, 10), true
}

func EncodeRegular(id int64) string {
	return encodeJavaFriendly(uint64(id))
}

func javaFriendlyValue(c byte) int {
	for i := 0; i < len(javaFriendlyAlphabet); i++ {
		if javaFriendlyAlphabet[i] == c {
			return i
		}
	}
	return -1
}

func encodeJavaFriendly(bits uint64) string {
	var buf [11]byte
	for i := len(buf) - 1; i >= 0; i-- {
		buf[i] = javaFriendlyAlphabet[bits&0x3f]
		bits >>= 6
	}

	for i := 0; i < len(buf)-1; i++ {
		if buf[i] != '0' {
			return string(buf[i:])
		}
	}
	return string(buf[len(buf)-1:])
}

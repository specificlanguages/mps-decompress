package decompress

import "strconv"

// Reimplementation of JetBrains MPS JavaFriendlyBase64 as used by
// jetbrains.mps.smodel.persistence.def.v9.IdEncoder for regular node IDs.
const javaFriendlyAlphabet = "0123456789abcdefghijklmnopqrstuvwxyz$_ABCDEFGHIJKLMNOPQRSTUVWXYZ"

func decodeRegularNodeID(text string) (string, bool) {
	if text == "" || text == "^" || text[0] == '~' {
		return "", false
	}

	var bits uint64
	for i := 0; i < len(text); i++ {
		value := javaFriendlyValue(text[i])
		if value < 0 {
			return "", false
		}
		bits = (bits << 6) | uint64(value)
	}

	if encodeJavaFriendly(bits) != text {
		return "", false
	}

	return strconv.FormatInt(int64(bits), 10), true
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

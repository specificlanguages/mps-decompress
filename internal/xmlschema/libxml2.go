package xmlschema

/*
#cgo pkg-config: libxml-2.0
#include <libxml/parser.h>
#include <libxml/xmlversion.h>

static int mopsLibXML2CompiledVersion(void) {
	return LIBXML_VERSION;
}
*/
import "C"
import "strconv"

// LinkedVersion proves that the binary can call into libxml2.
func LinkedVersion() string {
	C.xmlCheckVersion(C.mopsLibXML2CompiledVersion())
	return strconv.Itoa(int(C.mopsLibXML2CompiledVersion()))
}

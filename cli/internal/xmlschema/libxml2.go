package xmlschema

/*
#cgo pkg-config: libxml-2.0
#include <stdlib.h>
#include <libxml/parser.h>
#include <libxml/relaxng.h>
#include <libxml/xmlversion.h>

static int mopsLibXML2CompiledVersion(void) {
	return LIBXML_VERSION;
}

static void mopsRelaxNGNoopError(void *ctx, const char *msg, ...) {
}

static int mopsRelaxNGValidate(
	const char *schemaData,
	int schemaSize,
	const char *xmlData,
	int xmlSize
) {
	xmlRelaxNGParserCtxtPtr parser = xmlRelaxNGNewMemParserCtxt(schemaData, schemaSize);
	if (parser == NULL) {
		return -1;
	}
	xmlRelaxNGSetParserErrors(parser, mopsRelaxNGNoopError, mopsRelaxNGNoopError, NULL);
	xmlRelaxNGPtr schema = xmlRelaxNGParse(parser);
	xmlRelaxNGFreeParserCtxt(parser);
	if (schema == NULL) {
		return -2;
	}

	xmlDocPtr doc = xmlReadMemory(xmlData, xmlSize, "target.xml", NULL, XML_PARSE_NONET);
	if (doc == NULL) {
		xmlRelaxNGFree(schema);
		return -3;
	}

	xmlRelaxNGValidCtxtPtr validator = xmlRelaxNGNewValidCtxt(schema);
	if (validator == NULL) {
		xmlFreeDoc(doc);
		xmlRelaxNGFree(schema);
		return -4;
	}
	xmlRelaxNGSetValidErrors(validator, mopsRelaxNGNoopError, mopsRelaxNGNoopError, NULL);
	int result = xmlRelaxNGValidateDoc(validator, doc);
	xmlRelaxNGFreeValidCtxt(validator);
	xmlFreeDoc(doc);
	xmlRelaxNGFree(schema);
	return result;
}
*/
import "C"
import (
	_ "embed"
	"fmt"
	"strconv"
	"unsafe"
)

//go:embed mps-persistence.rng
var mpsPersistenceSchema []byte

// LinkedVersion proves that the binary can call into libxml2.
func LinkedVersion() string {
	C.xmlCheckVersion(C.mopsLibXML2CompiledVersion())
	return strconv.Itoa(int(C.mopsLibXML2CompiledVersion()))
}

func ValidateMPSPersistence(data []byte) error {
	schemaPtr := C.CBytes(mpsPersistenceSchema)
	defer C.free(unsafe.Pointer(schemaPtr))
	xmlPtr := C.CBytes(data)
	defer C.free(unsafe.Pointer(xmlPtr))

	result := C.mopsRelaxNGValidate(
		(*C.char)(schemaPtr),
		C.int(len(mpsPersistenceSchema)),
		(*C.char)(xmlPtr),
		C.int(len(data)),
	)
	if result == 0 {
		return nil
	}
	if result > 0 {
		return fmt.Errorf("XML does not match MPS persistence grammar")
	}
	return fmt.Errorf("libxml2 RELAX NG validation failed internally: %d", int(result))
}

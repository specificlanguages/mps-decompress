package expand

import (
	"bytes"
	"encoding/xml"
	"fmt"
	"io"
	"strings"
)

type prettyXMLEncoder struct {
	w            io.Writer
	stack        []elementState
	depth        int
	pendingStart bool
	wroteAny     bool
}

type elementState struct {
	name     xml.Name
	hasChild bool
	hasText  bool
}

func newPrettyXMLEncoder(w io.Writer) *prettyXMLEncoder {
	return &prettyXMLEncoder{w: w}
}

func (e *prettyXMLEncoder) Start(elem xml.StartElement) error {
	if err := e.closePendingStart(); err != nil {
		return err
	}

	if len(e.stack) > 0 {
		parent := &e.stack[len(e.stack)-1]
		parent.hasChild = true
		if !parent.hasText {
			if err := e.newlineAndIndent(); err != nil {
				return err
			}
		}
	} else if e.wroteAny {
		if _, err := io.WriteString(e.w, "\n"); err != nil {
			return err
		}
	}

	if _, err := fmt.Fprintf(e.w, "<%s", elem.Name.Local); err != nil {
		return err
	}
	for _, attr := range elem.Attr {
		if _, err := fmt.Fprintf(e.w, ` %s="%s"`, attr.Name.Local, escapeAttr(attr.Value)); err != nil {
			return err
		}
	}

	e.stack = append(e.stack, elementState{name: elem.Name})
	e.pendingStart = true
	e.depth++
	e.wroteAny = true
	return nil
}

func (e *prettyXMLEncoder) End(elem xml.EndElement) error {
	if len(e.stack) == 0 {
		return fmt.Errorf("unexpected end element %q", elem.Name.Local)
	}

	e.depth--
	state := e.stack[len(e.stack)-1]
	e.stack = e.stack[:len(e.stack)-1]

	if e.pendingStart {
		e.pendingStart = false
		_, err := io.WriteString(e.w, " />")
		return err
	}

	if state.hasChild && !state.hasText {
		if err := e.newlineAndIndent(); err != nil {
			return err
		}
	}

	_, err := fmt.Fprintf(e.w, "</%s>", state.name.Local)
	return err
}

func (e *prettyXMLEncoder) CharData(data xml.CharData) error {
	if err := e.closePendingStart(); err != nil {
		return err
	}
	if len(e.stack) > 0 {
		e.stack[len(e.stack)-1].hasText = true
	}

	var escaped bytes.Buffer
	if err := xml.EscapeText(&escaped, data); err != nil {
		return err
	}
	_, err := e.w.Write(escaped.Bytes())
	return err
}

func (e *prettyXMLEncoder) Token(tok xml.Token) error {
	if err := e.closePendingStart(); err != nil {
		return err
	}

	switch t := tok.(type) {
	case xml.ProcInst:
		if e.wroteAny {
			if _, err := io.WriteString(e.w, "\n"); err != nil {
				return err
			}
		}
		if _, err := fmt.Fprintf(e.w, "<?%s", t.Target); err != nil {
			return err
		}
		if len(t.Inst) > 0 {
			if _, err := fmt.Fprintf(e.w, " %s", string(t.Inst)); err != nil {
				return err
			}
		}
		_, err := io.WriteString(e.w, "?>")
		e.wroteAny = true
		return err
	case xml.Comment:
		if len(e.stack) > 0 {
			parent := &e.stack[len(e.stack)-1]
			parent.hasChild = true
			if !parent.hasText {
				if err := e.newlineAndIndent(); err != nil {
					return err
				}
			}
		} else if e.wroteAny {
			if _, err := io.WriteString(e.w, "\n"); err != nil {
				return err
			}
		}
		_, err := fmt.Fprintf(e.w, "<!--%s-->", string(t))
		e.wroteAny = true
		return err
	case xml.Directive:
		if e.wroteAny {
			if _, err := io.WriteString(e.w, "\n"); err != nil {
				return err
			}
		}
		_, err := fmt.Fprintf(e.w, "<!%s>", string(t))
		e.wroteAny = true
		return err
	default:
		return nil
	}
}

func (e *prettyXMLEncoder) Flush() error {
	if len(e.stack) != 0 {
		return fmt.Errorf("unclosed element %q", e.stack[len(e.stack)-1].name.Local)
	}
	if e.wroteAny {
		_, err := io.WriteString(e.w, "\n")
		return err
	}
	return nil
}

func (e *prettyXMLEncoder) closePendingStart() error {
	if !e.pendingStart {
		return nil
	}
	e.pendingStart = false
	_, err := io.WriteString(e.w, ">")
	return err
}

func (e *prettyXMLEncoder) newlineAndIndent() error {
	if _, err := io.WriteString(e.w, "\n"); err != nil {
		return err
	}
	_, err := io.WriteString(e.w, strings.Repeat("  ", e.depth))
	return err
}

func escapeAttr(value string) string {
	var b strings.Builder
	for _, r := range value {
		switch r {
		case '&':
			b.WriteString("&amp;")
		case '<':
			b.WriteString("&lt;")
		case '"':
			b.WriteString("&quot;")
		case '\n':
			b.WriteString("&#xA;")
		case '\r':
			b.WriteString("&#xD;")
		case '\t':
			b.WriteString("&#x9;")
		default:
			b.WriteRune(r)
		}
	}
	return b.String()
}

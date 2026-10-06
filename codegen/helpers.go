package codegen

import (
	"encoding/hex"
	"fmt"
	"sort"
	"strings"
	"unicode"
)

type methodEntry struct {
	Name   string
	Method *Method
}

func sortMethodsByINS(methods map[string]*Method) []methodEntry {
	entries := make([]methodEntry, 0, len(methods))
	for name, method := range methods {
		entries = append(entries, methodEntry{Name: name, Method: method})
	}

	sort.Slice(entries, func(i, j int) bool {
		left := entries[i]
		right := entries[j]

		leftINS := byte(0)
		rightINS := byte(0)
		if left.Method != nil {
			leftINS = left.Method.INS
		}
		if right.Method != nil {
			rightINS = right.Method.INS
		}
		if leftINS != rightINS {
			return leftINS < rightINS
		}
		return left.Name < right.Name
	})

	return entries
}

type statusWordEntry struct {
	name   string
	status StatusWord
}

func sortStatusWords(appletName string, statusWords map[string]StatusWord) []statusWordEntry {
	entries := make([]statusWordEntry, 0, len(statusWords))
	for name, sw := range statusWords {
		entries = append(entries, statusWordEntry{name: name, status: sw})
	}

	if appletName == "Counter" {
		order := []string{"SW_UNDERFLOW", "SW_OVERFLOW", "SW_NO_DATA", "SW_DATA_TOO_LONG"}
		ordered := make([]statusWordEntry, 0, len(entries))
		used := make(map[string]bool, len(entries))
		for _, key := range order {
			for _, entry := range entries {
				if entry.name == key {
					ordered = append(ordered, entry)
					used[entry.name] = true
					break
				}
			}
		}

		rest := make([]statusWordEntry, 0, len(entries)-len(ordered))
		for _, entry := range entries {
			if !used[entry.name] {
				rest = append(rest, entry)
			}
		}
		sort.Slice(rest, func(i, j int) bool {
			return rest[i].name < rest[j].name
		})
		return append(ordered, rest...)
	}

	sort.Slice(entries, func(i, j int) bool {
		if entries[i].status.Code != entries[j].status.Code {
			return entries[i].status.Code < entries[j].status.Code
		}
		return entries[i].name < entries[j].name
	})
	return entries
}

func parseAIDBytes(aid string) ([]byte, error) {
	decoded, err := hex.DecodeString(strings.TrimSpace(aid))
	if err != nil {
		return nil, fmt.Errorf("decode applet AID %q: %w", aid, err)
	}
	if len(decoded) == 0 {
		return nil, fmt.Errorf("decode applet AID %q: empty AID", aid)
	}
	return decoded, nil
}

func swiftTypeName(name string) string {
	if name == "" {
		return "Applet"
	}

	parts := strings.FieldsFunc(name, func(r rune) bool {
		return r == '_' || r == '-' || r == ' '
	})
	if len(parts) == 0 {
		return upperFirst(name)
	}

	var b strings.Builder
	for _, part := range parts {
		if part == "" {
			continue
		}
		b.WriteString(upperFirst(part))
	}
	if b.Len() == 0 {
		return "Applet"
	}
	return b.String()
}

func upperFirst(s string) string {
	if s == "" {
		return s
	}
	runes := []rune(s)
	runes[0] = unicode.ToUpper(runes[0])
	return string(runes)
}

func responseStructName(appletName, methodName string) string {
	if strings.HasPrefix(methodName, "get") && len(methodName) > 3 {
		return appletName + upperFirst(methodName[3:])
	}
	return appletName + upperFirst(methodName) + "Response"
}

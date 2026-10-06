package codegen

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/relux-works/javacard-rpc/pluginapi"
)

type parityFixture struct {
	Name        string
	Schema      *Schema
	Namespace   string
	Files       []pluginapi.File
	InputSHA256 string
}

func loadFixture(t *testing.T, name string) parityFixture {
	t.Helper()
	b, err := os.ReadFile(filepath.Join("testdata", name))
	if err != nil {
		t.Fatal(err)
	}
	var f parityFixture
	if err := json.Unmarshal(b, &f); err != nil {
		t.Fatal(err)
	}
	return f
}

func parseCounter(t *testing.T) *Schema { return loadFixture(t, "parity-1-0.json").Schema }
func intPtr(v int) *int                 { return &v }
func requireContains(t *testing.T, source, want string) {
	t.Helper()
	if !strings.Contains(source, want) {
		t.Fatalf("missing %q", want)
	}
}
func generateClientForTest(s *Schema, namespace string) ([]byte, error) {
	files, err := (Plugin{}).Generate(s, pluginapi.Options{Namespace: namespace})
	if err != nil {
		return nil, err
	}
	return files[2].Data, nil
}

func comparePackage(want, got []pluginapi.File) error {
	if len(got) != len(want) {
		return fmt.Errorf("file count: want %d, got %d", len(want), len(got))
	}
	for i := range want {
		if got[i].Name != want[i].Name {
			return fmt.Errorf("ordered file %d: want %s, got %s", i, want[i].Name, got[i].Name)
		}
		if got[i].Data == nil || !bytes.Equal(got[i].Data, want[i].Data) {
			return fmt.Errorf("bytes differ: %s", want[i].Name)
		}
	}
	return nil
}

// Plugin.Generate matches independent v0.4.5 bytes and donor ordering for four
// IDLs, two namespaces, three workspace/memory choices and three simulator options.
func TestPluginReleasedParity(t *testing.T) {
	paths, err := filepath.Glob("testdata/parity-*.json")
	if err != nil {
		t.Fatal(err)
	}
	if len(paths) != 8 {
		t.Fatalf("want 8 pinned fixtures, got %d", len(paths))
	}
	for _, path := range paths {
		f := loadFixture(t, filepath.Base(path))
		for _, workspace := range []string{"", "transient", "persistent"} {
			for _, memory := range []string{"", "clear_on_deselect", "clear_on_reset"} {
				for _, sim := range []string{"", "com.klinec:jcardsim:3.0.5.9", "works.relux:jcardsim:3.0.5.9-relux.1"} {
					t.Run(f.Name+"/"+workspace+"/"+memory+"/"+sim, func(t *testing.T) {
						f.Schema.Applet.StreamWorkspace = workspace
						before, err := json.Marshal(f.Schema)
						if err != nil {
							t.Fatal(err)
						}
						got, err := (Plugin{}).Generate(f.Schema, pluginapi.Options{Namespace: f.Namespace, StreamMemory: memory, SimulatorDependency: sim})
						if err != nil {
							t.Fatal(err)
						}
						if err := comparePackage(f.Files, got); err != nil {
							t.Fatal(err)
						}
						after, err := json.Marshal(f.Schema)
						if err != nil {
							t.Fatal(err)
						}
						if !bytes.Equal(before, after) {
							t.Fatal("Generate mutated caller schema")
						}
					})
				}
			}
		}
	}
}

// The parity comparator rejects a one-byte drift, missing/extra files and reordering;
// the unchanged package is the paired positive control.
func TestParityCheckerRejectsBoundedByteChange(t *testing.T) {
	f := loadFixture(t, "parity-2-0.json")
	if err := comparePackage(f.Files, f.Files); err != nil {
		t.Fatal(err)
	}
	changed := append([]pluginapi.File(nil), f.Files...)
	changed[0].Data = bytes.Clone(changed[0].Data)
	changed[0].Data[0] ^= 1
	if err := comparePackage(f.Files, changed); err == nil || err.Error() != "bytes differ: settings.gradle.kts" {
		t.Fatalf("byte drift verdict: %v", err)
	}
	for _, got := range [][]pluginapi.File{f.Files[:2], append(append([]pluginapi.File(nil), f.Files...), f.Files[0]), {f.Files[1], f.Files[0], f.Files[2]}} {
		if err := comparePackage(f.Files, got); err == nil {
			t.Fatal("inventory/order drift admitted")
		}
	}
}

// Renderer refusals return their specific error and no partial files, preserving
// the caller's schema. Model/option validation outside these renderer checks is facade-owned.
func TestPluginRefusalsReturnNoFiles(t *testing.T) {
	cases := []struct {
		name            string
		mutate          func(*Schema)
		namespace, want string
		nilSchema       bool
	}{
		{name: "nil-schema", namespace: "demo", want: "schema is nil", nilSchema: true},
		{name: "empty-namespace", namespace: " \t", want: "package name is empty"},
		{name: "empty-aid", namespace: "demo", mutate: func(s *Schema) { s.Applet.AID = "" }, want: "decode applet AID \"\": empty AID"},
		{name: "odd-aid", namespace: "demo", mutate: func(s *Schema) { s.Applet.AID = "A" }, want: "decode applet AID \"A\": encoding/hex: odd length hex string"},
		{name: "nil-method", namespace: "demo", mutate: func(s *Schema) { s.Methods = map[string]*Method{"bad": nil} }, want: "method \"bad\" is nil"},
		{name: "unknown-type", namespace: "demo", mutate: func(s *Schema) {
			s.Methods = map[string]*Method{"bad": {Name: "bad", Request: &Message{Fields: []Field{{Name: "x", Type: "unknown", Location: ParameterLocationData}}}}}
		}, want: "method \"bad\" request field \"x\": unsupported field type \"unknown\""},
		{name: "unknown-location", namespace: "demo", mutate: func(s *Schema) {
			s.Methods = map[string]*Method{"bad": {Name: "bad", Request: &Message{Fields: []Field{{Name: "x", Type: FieldTypeU8, Location: "elsewhere"}}}}}
		}, want: "method \"bad\" request field \"x\": unsupported location \"elsewhere\""},
		{name: "nonfinal-variable-response", namespace: "demo", mutate: func(s *Schema) {
			s.Methods = map[string]*Method{"bad": {Name: "bad", Response: &Message{Fields: []Field{{Name: "x", Type: FieldTypeBytes}, {Name: "y", Type: FieldTypeU8}}}}}
		}, want: "method \"bad\" response field \"x\": variable-length bytes field must be the last response field"},
	}
	valid := parseCounter(t)
	if files, err := (Plugin{}).Generate(valid, pluginapi.Options{Namespace: "demo"}); err != nil || len(files) != 3 {
		t.Fatalf("positive control: %v", err)
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			s := parseCounter(t)
			if c.nilSchema {
				s = nil
			}
			if c.mutate != nil {
				c.mutate(s)
			}
			before, _ := json.Marshal(s)
			files, err := (Plugin{}).Generate(s, pluginapi.Options{Namespace: c.namespace})
			if err == nil || err.Error() != c.want || files != nil {
				t.Fatalf("want %q and nil files, got %v / %v", c.want, err, files)
			}
			after, _ := json.Marshal(s)
			if !bytes.Equal(before, after) {
				t.Fatal("refusal mutated input")
			}
		})
	}
}

// The actual compiled backend dependency graph has only the released plugin API
// beyond this module and the standard library, with no local replacement.
func TestPluginDependencyBoundary(t *testing.T) {
	cmd := exec.Command("go", "list", "-deps", "-json", ".")
	out, err := cmd.Output()
	if err != nil {
		t.Fatal(err)
	}
	decoder := json.NewDecoder(bytes.NewReader(out))
	seen := map[string]bool{}
	for decoder.More() {
		var p struct {
			Standard   bool
			ImportPath string
			Module     *struct {
				Path, Version string
				Replace       any
			}
		}
		if err := decoder.Decode(&p); err != nil {
			t.Fatal(err)
		}
		if p.Standard {
			continue
		}
		if p.Module == nil {
			t.Fatalf("no module for %s", p.ImportPath)
		}
		if p.Module.Path == "github.com/relux-works/javacard-rpc-client-kotlin" {
			continue
		}
		if p.Module.Path != "github.com/relux-works/javacard-rpc/pluginapi" || p.Module.Version != "v0.1.0" || p.Module.Replace != nil {
			t.Fatalf("forbidden dependency: %+v", p)
		}
		seen[p.Module.Path] = true
	}
	if !reflect.DeepEqual(seen, map[string]bool{"github.com/relux-works/javacard-rpc/pluginapi": true}) {
		t.Fatalf("API dependency missing: %v", seen)
	}
}

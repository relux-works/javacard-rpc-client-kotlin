package codegen

import (
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"testing"

	"github.com/relux-works/javacard-rpc/pluginapi"
)

// Generates through Plugin.Generate, compiles with this repo's real JVM runtime,
// and executes retained and additional stream/response regression tests. The frozen
// Java endpoint and JCSystem fixture model JVM behavior, not physical-card lifecycle.
func TestGeneratedKotlinStreamClientHarness(t *testing.T) {
	root, err := filepath.Abs("..")
	if err != nil {
		t.Fatal(err)
	}
	work := filepath.Join(root, ".temp", "kotlin-contract")
	if err := os.MkdirAll(work, 0755); err != nil {
		t.Fatal(err)
	}
	project, err := os.MkdirTemp(work, "run-")
	if err != nil {
		t.Fatal(err)
	}
	t.Logf("JVM project and reports: %s", project)
	if err := os.CopyFS(project, os.DirFS("testdata/jvm")); err != nil {
		t.Fatal(err)
	}
	raw, err := os.ReadFile("testdata/harness-schema.json")
	if err != nil {
		t.Fatal(err)
	}
	var schema Schema
	if err := json.Unmarshal(raw, &schema); err != nil {
		t.Fatal(err)
	}
	files, err := (Plugin{}).Generate(&schema, pluginapi.Options{Namespace: "io.jcrpc.streamdemo.client"})
	if err != nil {
		t.Fatal(err)
	}
	for _, file := range files {
		path := filepath.Join(project, filepath.FromSlash(file.Name))
		if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(path, file.Data, 0644); err != nil {
			t.Fatal(err)
		}
	}
	settings := string(files[0].Data) + "\nincludeBuild(" + strconv.Quote(filepath.ToSlash(root)) + ") {\n    dependencySubstitution {\n        substitute(module(\"io.jcrpc:javacard-rpc-client-kotlin\")).using(project(\":\"))\n    }\n}\n"
	if err := os.WriteFile(filepath.Join(project, "settings.gradle.kts"), []byte(settings), 0644); err != nil {
		t.Fatal(err)
	}
	args := []string{"-p", project, "test", "--no-daemon", "--console=plain"}
	if filter := os.Getenv("JCRPC_KOTLIN_TEST_FILTER"); filter != "" {
		args = append(args, "--tests", filter)
	}
	cmd := exec.Command(filepath.Join(root, "gradlew"), args...)
	cmd.Dir = root
	output, err := cmd.CombinedOutput()
	if e := os.WriteFile(filepath.Join(project, "gradle.log"), output, 0644); e != nil {
		t.Fatal(e)
	}
	if err != nil {
		t.Fatalf("generated Kotlin harness: %v\n%s", err, output)
	}
	// A green process must include executed tests, not a skipped/empty task.
	reports, err := filepath.Glob(filepath.Join(project, "build/test-results/test/TEST-*.xml"))
	if err != nil || len(reports) != 1 {
		t.Fatalf("expected stream harness report: %v / %v", reports, err)
	}
	b, err := os.ReadFile(reports[0])
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(b), "<testcase name=") {
		t.Fatal("no executed Kotlin tests")
	}
	if os.Getenv("JCRPC_KOTLIN_TEST_FILTER") == "" && strings.Count(string(b), "<testcase name=") != 11 {
		t.Fatalf("want 11 tests including 7 retained regressions, got report:\n%s", b)
	}
}

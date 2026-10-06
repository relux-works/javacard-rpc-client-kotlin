// check-mutants exercises narrowing source mutants in disposable task-local
// copies. It never modifies the candidate and requires a named behavioral failure.
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

type mutant struct{ ID, File, Before, After, Test, JVMTest, Bound string }
type receipt struct {
	Mutant, Bound, NamedTest, Log string
	Exit                          int
	Killed                        bool
}

func main() {
	selection := flag.String("only", "", "comma-separated mutant IDs (empty selects all)")
	out := flag.String("out", ".temp/backend-mutants", "task-local output directory")
	flag.Parse()
	root, err := os.Getwd()
	must(err)
	apiDir, err := exec.Command("go", "list", "-m", "-f", "{{.Dir}}", "github.com/relux-works/javacard-rpc/pluginapi").Output()
	must(err)
	mutants := []mutant{
		{"api-local-replace", "../go.mod", "require github.com/relux-works/javacard-rpc/pluginapi v0.1.0", "require github.com/relux-works/javacard-rpc/pluginapi v0.1.0\n\nreplace github.com/relux-works/javacard-rpc/pluginapi => " + strings.TrimSpace(string(apiDir)), "TestPluginDependencyBoundary", "", "retains released API require/import but resolves just that dependency locally"},
		{"namespace-space", "gen_kotlin.go", `if strings.TrimSpace(packageName) == "" {`, `if strings.TrimSpace(packageName) == "" && packageName != " \t" {`, "TestPluginRefusalsReturnNoFiles/empty-namespace", "", "permits only whitespace namespace space+tab"},
		{"empty-aid", "helpers.go", `if len(decoded) == 0 {`, `if len(decoded) == 0 && aid != "" {`, "TestPluginRefusalsReturnNoFiles/empty-aid", "", "permits exactly empty AID while retaining malformed/whitespace rejection"},
		{"parity-first-byte", "plugin_test.go", `!bytes.Equal(got[i].Data, want[i].Data)`, `!bytes.Equal(got[i].Data[1:], want[i].Data[1:])`, "TestParityCheckerRejectsBoundedByteChange", "", "ignores only first-byte drift"},
		{"parity-extra-file", "plugin_test.go", `len(got) != len(want)`, `len(got) != len(want) && !(len(got) == len(want)+1 && got[len(want)].Name == want[0].Name)`, "TestParityCheckerRejectsBoundedByteChange", "", "admits one duplicate first file at end"},
		{"fixed-response-plus-one", "gen_kotlin.go", `if (response.data.size != %d) invalidResponse()`, `if (response.data.size != %d && response.data.size != 2) invalidResponse()`, "TestGeneratedKotlinStreamClientHarness", "generatedClientRequiresTheExactFixedResponseLength", "admits 2 bytes for 1-byte scalar response"},
		{"descriptor-plus-one", "gen_kotlin_stream.go", `if (data.size != 35) invalidResponse()`, `if (data.size != 35 && data.size != 36) invalidResponse() // if (data.size != 35) invalidResponse()`, "TestGeneratedKotlinStreamClientHarness", "generatedClientRejectsMalformedDescriptorsBeforeReading", "admits descriptor with one trailing byte; searched token retained in comment"},
		{"descriptor-max-plus-one", "gen_kotlin_stream.go", `totalLength > maxLength)`, `(totalLength > maxLength && totalLength != maxLength + 1))`, "TestGeneratedKotlinStreamClientHarness", "generatedClientRejectsMalformedDescriptorsBeforeReading", "admits maxLength+1 descriptor"},
		{"descriptor-count-one", "gen_kotlin_stream.go", `if (packetCount != expectedPacketCount)`, `if (packetCount != expectedPacketCount && packetCount != 1)`, "TestGeneratedKotlinStreamClientHarness", "generatedClientRejectsMalformedDescriptorsBeforeReading", "admits inconsistent packetCount 1"},
		{"upload-max-plus-one", "gen_kotlin_stream.go", `%s.size > %d) invalidResponse()`, `(%s.size > %d && ` + `"+name+"` + `.size != 1793)) invalidResponse()`, "TestGeneratedKotlinStreamClientHarness", "generatedClientRejectsUploadBoundsBeforeWriting", "admits exactly upload length 1793"},
		{"ack-one-zero", "gen_kotlin_stream.go", `if (response.data.isNotEmpty()) invalidResponse()`, `if (response.data.isNotEmpty() && !(response.data.size == 1 && response.data[0] == 0.toByte())) invalidResponse()`, "TestGeneratedKotlinStreamClientHarness", "generatedClientRejectsMalformedChunksAndAcknowledgements", "admits one-byte zero acknowledgement"},
		{"chunk-short-one", "gen_kotlin_stream.go", `if (chunkResponse.data.size != expectedLength)`, `if (chunkResponse.data.size != expectedLength && chunkResponse.data.size != expectedLength - 1)`, "TestGeneratedKotlinStreamClientHarness", "generatedClientRejectsMalformedChunksAndAcknowledgements", "admits chunk short by one before continuing read"},
		{"status-6985", "gen_kotlin.go", `if (sw != 0x9000u.toUShort())`, `if (sw != 0x9000u.toUShort() && sw != 0x6985u.toUShort())`, "TestGeneratedKotlinStreamClientHarness", "generatedClientRejectsErrorStatusWord", "admits only error SW 6985"},
		{"digest-first-bit", "gen_kotlin_stream.go", `!MessageDigest.getInstance(\"SHA-256\").digest(streamResult).contentEquals(streamDescriptor.digest)`, `!(MessageDigest.getInstance(\"SHA-256\").digest(streamResult).contentEquals(streamDescriptor.digest) || MessageDigest.getInstance(\"SHA-256\").digest(streamResult).let { d -> d[0] = (d[0].toInt() xor 1).toByte(); d.contentEquals(streamDescriptor.digest) })`, "TestGeneratedKotlinStreamClientHarness", "generatedClientAbortsGeneratedRuntimeWhenTheResultDigestDoesNotMatch", "admits only first digest bit flipped"},
		{"busy-payload-nine", "gen_kotlin_stream.go", "\n\tif requestStream != nil {\n\t\tbody = append(body, buildKotlinStreamUploadLines", "\n\tif methodName == \"processPacket\" { body[0] = strings.Replace(body[0], \")) throw\", \" ) && !(requestPacket.size == 1 && requestPacket[0] == 9.toByte())) throw\", 1) }\n\tif requestStream != nil {\n\t\tbody = append(body, buildKotlinStreamUploadLines", "TestGeneratedKotlinStreamClientHarness", "generatedClientRejectsASecondStreamOperationWithoutTouchingTheFirstSession", "admits concurrent processPacket with one-byte payload 9"},
	}
	var receipts []receipt
	for _, m := range mutants {
		if *selection != "" && !strings.Contains(","+*selection+",", ","+m.ID+",") {
			continue
		}
		dir := filepath.Join(root, *out, m.ID)
		must(os.MkdirAll(dir, 0755))
		for _, name := range []string{"codegen", "src", "gradle"} {
			must(os.CopyFS(filepath.Join(dir, name), os.DirFS(filepath.Join(root, name))))
		}
		for _, name := range []string{"go.mod", "go.sum", "gradlew", "gradle.properties", "settings.gradle.kts", "build.gradle.kts"} {
			b, e := os.ReadFile(filepath.Join(root, name))
			must(e)
			mode := os.FileMode(0644)
			if name == "gradlew" {
				mode = 0755
			}
			must(os.WriteFile(filepath.Join(dir, name), b, mode))
		}
		path := filepath.Join(dir, "codegen", m.File)
		b, e := os.ReadFile(path)
		must(e)
		if !strings.Contains(string(b), m.Before) {
			panic("anchor absent: " + m.ID)
		}
		must(os.WriteFile(path, []byte(strings.Replace(string(b), m.Before, m.After, 1)), 0644))
		cmd := exec.Command("go", "test", "./codegen", "-count=1", "-run", "^"+m.Test+"$", "-v")
		cmd.Dir = dir
		if m.JVMTest != "" {
			cmd.Env = append(os.Environ(), "JCRPC_KOTLIN_TEST_FILTER=*."+m.JVMTest)
		}
		output, e := cmd.CombinedOutput()
		code := 0
		if e != nil {
			if exit, ok := e.(*exec.ExitError); ok {
				code = exit.ExitCode()
			} else {
				panic(e)
			}
		}
		log := filepath.Join(dir, "mutant.log")
		must(os.WriteFile(log, output, 0644))
		name := m.Test
		if m.JVMTest != "" {
			name = m.JVMTest
		}
		killed := code == 1 && strings.Contains(string(output), "--- FAIL: "+strings.Split(m.Test, "/")[0]) && (m.JVMTest == "" || strings.Contains(string(output), m.JVMTest+"() FAILED"))
		receipts = append(receipts, receipt{m.ID, m.Bound, name, log, code, killed})
		b, e = json.MarshalIndent(receipts, "", "  ")
		must(e)
		must(os.WriteFile(filepath.Join(root, *out, "receipts.json"), append(b, '\n'), 0644))
		fmt.Printf("%s: exit=%d killed=%t named_test=%s\n", m.ID, code, killed, name)
	}
	if len(receipts) == 0 {
		panic("no mutants selected")
	}
	for _, r := range receipts {
		if !r.Killed {
			os.Exit(1)
		}
	}
}

func must(err error) {
	if err != nil {
		panic(err)
	}
}

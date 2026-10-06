// Package codegen renders Kotlin/JVM packages through the released plugin API.
package codegen

import (
	"github.com/relux-works/javacard-rpc/pluginapi"
	"path"
	"strings"
)

type Plugin struct{}

var _ pluginapi.Plugin = Plugin{}

func (Plugin) Generate(s *pluginapi.Schema, o pluginapi.Options) ([]pluginapi.File, error) {
	source, err := GenerateKotlinClient(s, o.Namespace)
	if err != nil {
		return nil, err
	}
	stem := strings.ToLower(kotlinStemName(s.Applet.Name))
	return []pluginapi.File{
		{Name: "settings.gradle.kts", Data: []byte(GenerateKotlinSettingsGradle(stem))},
		{Name: "build.gradle.kts", Data: []byte(GenerateKotlinBuildGradle(stem, o.Namespace, s.Applet.Version))},
		{Name: path.Join("src/main/kotlin", strings.ReplaceAll(o.Namespace, ".", "/"), KotlinSourceFileName(s.Applet.Name)), Data: source},
	}, nil
}

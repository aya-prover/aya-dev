// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.util.stream.Collectors

class GenerateLexerTokenTask extends DefaultTask {
  {
    className = "GeneratedLexerTokens"
    ymlName = "Aya-vscode"
    group = "build setup"
  }

  @OutputDirectory File outputDir
  @OutputDirectory File ymlOutputDir
  @Input String className
  @Input String ymlName
  @InputFile def lexerG4 = ""
  @Input def basePackage = ""

  @TaskAction def run() {
    final var START = "// ---- AyaLexer begin: Keywords"
    final var END = "// ---- AyaLexer end: Keywords"

    var keywords = new HashMap<String, String>()
    var inside = false
    try (var reader = new BufferedReader(new FileReader(lexerG4))) {
      reader.lines().forEach(line -> {
        if (line == START) inside = true
        else if (line == END) inside = false
        else if (inside && !line.isEmpty() && !line.startsWith("//")) {
          var lineNoColon = line.substring(0, line.lastIndexOf(';'))
          var a = lineNoColon.split(":", 2)
          var token = a[0].trim()
          var text = a[1].split("\\|")[0].trim()
          text = text.substring(1, text.length() - 1)
          keywords.put(token, text)
        }
      })
    }

    outputDir.mkdirs()
    ymlOutputDir.mkdirs()

    genJavaCode(keywords)
    genYmlCode(keywords)
  }

  def genYmlCode(HashMap<String, String> keywords) {
    var content = keywords.values().stream().collect(Collectors.joining("|"))
    def code = """\
#
# GENERATED FROM AyaLexer.g4
# Syntax highlighting for Aya, used by VSCode.
# This file contains only keywords, because we use semantic highlighting through LSP.
#

scopeName: source.minimal-aya
name: Aya
fileTypes:
- aya

patterns:
- # Line comments
  begin: '--'
  end: '\$'
  name: comment.line.double-dash.aya
- # Block comments
  begin: '{-'
  end: '-}'
  name: comment.block.aya
- # Keywords
  match: '\\b($content)\\b'
  name: keyword.other.aya
- # Arrows & commas
  match: '=>|->|,'
  name: keyword.other.aya
- # String literals
  begin: '"'
  end: '"'
  name: string.quoted.double.aya
- # Numeric literals
  match: '\\b-?\\d+\\b'
  name: constant.numeric.aya
# End
    """.stripIndent()
    def outFile = new File(ymlOutputDir, "${ymlName}.yml")
    if (!outFile.exists()) assert outFile.createNewFile()
    outFile.write(code)
  }

  def genJavaCode(HashMap<String, String> keywords) {
    var content = keywords.entrySet().stream().map(e ->
      String.format("entry(AyaLexer.${e.key}, \"${e.value}\")")
    ).collect(Collectors.joining(",\n"))

    def code = """\
      package ${basePackage};
      import static java.util.Map.entry;
      import java.util.Map;
      public class $className {
        public static final Map<Integer, String> KEYWORDS = Map.ofEntries(
          $content
        );
      }""".stripIndent()
    def outFile = new File(outputDir, "${className}.java")
    if (!outFile.exists()) assert outFile.createNewFile()
    outFile.write(code)
  }
}

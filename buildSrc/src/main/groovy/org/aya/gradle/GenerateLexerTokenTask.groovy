// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.util.stream.Collectors

class GenerateLexerTokenTask extends DefaultTask {
  {
    className = "GeneratedLexerTokens"
    group = "build setup"
  }

  @OutputDirectory File outputDir
  @Input String className
  @Input def lexerG4 = ""
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
          var lineNoColon = line.substring(0, line.lastIndexOf(';'));
          var a = lineNoColon.split(":", 2);
          var token = a[0].trim()
          var text = a[1].split("\\|")[0].trim()
          text = text.substring(1, text.length() - 1)
          keywords.put(token, text)
        }
      })
    }

    var content = keywords.entrySet().stream().map(e ->
      String.format("entry(AyaLexer.${e.key}, \"${e.value}\")")
    ).collect(Collectors.joining(",\n"))

    // TODO: parse lexerG4
    def code = """\
      package ${basePackage};
      import static java.util.Map.entry;
      import java.util.Map;
      public class $className {
        public static final Map<Integer, String> KEYWORDS = Map.ofEntries(
          $content
        );
      }""".stripIndent()
    outputDir.mkdirs()
    def outFile = new File(outputDir, "${className}.java")
    if (!outFile.exists()) assert outFile.createNewFile()
    outFile.write(code)
  }
}

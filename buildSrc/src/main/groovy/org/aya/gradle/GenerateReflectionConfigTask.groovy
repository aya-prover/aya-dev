// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.IntStream

class GenerateReflectionConfigTask extends DefaultTask {
  {
    group = "build setup"
  }

  @OutputDirectory File outputDir
  @InputFile File inputFile

  private var pattern = Pattern.compile("\\{(\\d+)\\,\\s*(\\d+)\\}")

  @TaskAction def run() {
    var lines = Files.lines(inputFile.toPath())
      .filter(line -> !line.startsWith("#"))
      .filter(line -> !line.isEmpty())
      .flatMap(line -> expand(line).stream())
      .toList()

    var reflectConfig = lines.stream()
      .map(line -> generateReflectConfig(line))
      .collect(Collectors.joining(",\n", "[\n", "]\n"))
    var serializeConfig = lines.stream()
      .map(line -> generateSerializeConfig(line))
      .collect(Collectors.joining(",\n", "[\n", "]\n"))
    Files.writeString(outputDir.toPath().resolve("reflect-config.json"), reflectConfig)
    Files.writeString(outputDir.toPath().resolve("serialization-config.json"), serializeConfig)
  }

  List<String> expand(String line) {
    var matcher = pattern.matcher(line)
    if (!matcher.find()) return List.of(line)
    int start = matcher.group(1) as int
    int end = matcher.group(2) as int
    var strip = line.substring(0, matcher.start())
    return IntStream.rangeClosed(start, end)
      .mapToObj(i -> String.format("%s%d", strip, i))
      .toList()
  }

  static String generateReflectConfig(String className) {
    return """\
    {
      "name": "$className",
      "unsafeAllocated": true,
      "allDeclaredFields": true,
      "allDeclaredMethods": true,
      "allPublicMethods": true,
      "allDeclaredConstructors": true
    }
    """.stripIndent()
  }

  static String generateSerializeConfig(String className) {
    return """{"name": "$className"}"""
  }
}

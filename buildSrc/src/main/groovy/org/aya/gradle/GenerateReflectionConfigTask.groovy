// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.stream.Stream

class GenerateReflectionConfigTask extends DefaultTask {
  {
    group = "build setup"
  }

  @OutputDirectory File outputDir
  @InputFile File inputFile
  @Optional @InputDirectory File extraDir
  @Input List<String> classPrefixes = []
  @Input List<String> excludeNamesSuffix = []
  @Optional @Input String packageName

  private static var pattern = Pattern.compile("\\{(\\d+),\\s*(\\d+)}")
  private static var serializable = Pattern.compile("(record|enum)\\s*([a-zA-Z0-9_]+)")

  @TaskAction def run() {
    var lines = Files.lines(inputFile.toPath())
      .filter(line -> !line.startsWith("#"))
      .filter(line -> !line.isEmpty())
      .flatMap(line -> expand(line).stream())
    var extraLines = extraDir == null ? Stream.<String> empty()
      : Files.list(extraDir.toPath())
      .filter { path -> classPrefixes.any(path.getFileName().toString()::startsWith) }
      .flatMap { path ->
        var className = path.getFileName().toString()
        className = className.substring(0, className.indexOf(".java"))
        var subClasses = serializable.matcher(Files.readString(path, StandardCharsets.UTF_8)).results()
          .map { it -> it.group(2) }
          .map { name -> className + "\$" + name }
          .filter { name -> !excludeNamesSuffix.contains(name) }
          .map { name -> packageName + "." + name }
        return Stream.concat(Stream.of(packageName + "." + className), subClasses)
      }
    var stream = Stream.concat(lines, extraLines).toList()

    var reflectConfig = stream.stream()
      .map(line -> generateReflectConfig(line))
      .collect(Collectors.joining(",\n", "[\n", "]\n"))
    var serializeConfig = stream.stream()
      .map(line -> generateSerializeConfig(line))
      .collect(Collectors.joining(",\n", "[\n", "]\n"))
    Files.writeString(outputDir.toPath().resolve("reflect-config.json"), reflectConfig)
    Files.writeString(outputDir.toPath().resolve("serialization-config.json"), serializeConfig)
  }

  static List<String> expand(String line) {
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

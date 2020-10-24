// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class GenerateVersionTask extends DefaultTask {
  @Input
  def taskVersion = project.version
  @Input
  def basePackage = project.group
  @Input
  def className = "GeneratedVersion"
  @OutputDirectory
  def outputDir = project.file("src/main/java/org/mzi/prelude")

  @TaskAction
  def run() {
    def code = """\
      package ${basePackage}.prelude;
      import ${basePackage}.util.Version;
      import org.jetbrains.annotations.NotNull;
      public class $className {
        public static final @NotNull String VERSION_STRING = "$taskVersion";
        public static final @NotNull Version VERSION = Version.create(VERSION_STRING);
      }""".stripIndent()
    outputDir.mkdirs()
    def outFile = new File(outputDir, "${className}.java")
    if (!outFile.exists()) assert outFile.createNewFile()
    outFile.write(code)
  }
}

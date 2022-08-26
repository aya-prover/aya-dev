// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class GenerateVersionTask extends DefaultTask {
  {
    className = "GeneratedVersion"
    group = "build setup"
  }

  final @InputDirectory File inputDir = project.rootProject.file(".git")
  @OutputDirectory File outputDir
  @Input String className
  @Input def basePackage = project.group
  @Input final def taskVersion = project.version

  @TaskAction def run() {
    def stdout = BuildUtil.gitRev(project.rootDir)
    def code = """\
      package ${basePackage}.prelude;
      import ${basePackage}.util.Version;
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.NonNls;
      public class $className {
        public static final @NotNull @NonNls String VERSION_STRING = "$taskVersion";
        public static final @NotNull @NonNls String COMMIT_HASH = "${stdout.toString().trim()}";
        public static final @NotNull Version VERSION = Version.create(VERSION_STRING);
      }""".stripIndent()
    outputDir.mkdirs()
    def outFile = new File(outputDir, "${className}.java")
    if (!outFile.exists()) {
      // Side effects
      def result = outFile.createNewFile()
      assert result
    }
    outFile.write(code)
  }
}

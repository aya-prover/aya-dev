// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class GenerateVersionTask extends DefaultTask {
  {
    className = "GeneratedVersion"
    group = "build setup"
  }

  final @InputFiles File dotGitDir = project.rootProject.file(".git")
  @OutputDirectory File outputDir
  @Input String className
  @Input def basePackage = project.group
  @Input final def taskVersion = project.version
  @Input int jdkVersion

  @TaskAction def run() {
    def stdout = "__COMMIT_HASH__"
    def commitHashJavadoc = "/// .git missing, maybe in some sandbox environment (e.g. Nix), use a placeholder for substitution"
    if (dotGitDir.exists()) {
      // This will be trimmed inside BuildUtil.gitRev
      stdout = BuildUtil.gitRev(project.rootDir)
      commitHashJavadoc = "/// Generated from git rev output"
    }
    def code = """\
      package ${basePackage}.prelude;
      import ${basePackage}.util.Version;
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.NonNls;
      public class $className {
        public static final @NotNull @NonNls String VERSION_STRING = "$taskVersion";
        $commitHashJavadoc
        public static final @NotNull @NonNls String COMMIT_HASH = "$stdout";
        public static final @NotNull @NonNls String JDK_VERSION = "$jdkVersion";
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

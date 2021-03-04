// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.gradle

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class GenerateVersionTask extends WriteFileTask {
  {
    className = "GeneratedVersion"
    group = "build setup"
  }

  @Input def taskVersion = project.version

  @TaskAction def run() {
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

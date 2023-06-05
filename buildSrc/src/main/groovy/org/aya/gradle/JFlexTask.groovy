// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.gradle

import org.aya.gradle.jflex.JFlexUtil
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class JFlexTask extends DefaultTask implements Runnable {
  @OutputDirectory File outputDir
  @InputFile File jflex

  @TaskAction void run() {
    JFlexUtil.invokeJflex(outputDir, jflex, project.rootDir)
  }
}

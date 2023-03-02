// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.gradle

import jflex.core.OptionUtils
import jflex.generator.LexGenerator
import jflex.l10n.ErrorMessages
import jflex.option.Options
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.nio.charset.StandardCharsets

class JFlexTask extends DefaultTask implements Runnable {
  @OutputDirectory File outputDir
  @InputFile File jflex
  @InputFile File skel

  @TaskAction void run() {
    OptionUtils.setSkeleton(skel)
    Options.setRootDirectory(project.rootDir)
    Options.encoding = StandardCharsets.UTF_8
    OptionUtils.setDir(outputDir)
    Options.no_minimize = false
    Options.no_backup = true
    Options.enable ErrorMessages.MACRO_UNUSED
    Options.enable ErrorMessages.EMPTY_MATCH
    Options.progress = false
    new LexGenerator(jflex).generate()
  }
}

// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.gradle.CommonTasks

dependencies {
  api(project(":tools-repl"))
  api(project(":cli-impl"))
  implementation(project(":producer"))
  implementation(libs.picocli.runtime)
  annotationProcessor(libs.picocli.codegen)
  implementation(libs.jline.terminal.jansi)
  implementation(libs.jline.builtins)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.hamcrest)
}

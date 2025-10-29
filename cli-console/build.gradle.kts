// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.gradle.CommonTasks

plugins { application }

val mainClassQName = "org.aya.cli.console.Main"
application.mainClass.set(mainClassQName)

dependencies {
  api(project(":tools-repl"))
  api(project(":cli-impl"))
  implementation(project(":producer"))
  implementation(project(":jit-compiler"))
  implementation(libs.kala.gson)
  implementation(libs.picocli.runtime)
  annotationProcessor(libs.picocli.codegen)
  implementation(libs.jline.terminal.native)
  implementation(libs.jline.builtins)
}

CommonTasks.fatJar(project, mainClassQName)

tasks.withType<AbstractCopyTask>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<JavaExec>("run") {
  standardInput = System.`in`
}

tasks.withType<JavaCompile>().configureEach { CommonTasks.picocli(this) }

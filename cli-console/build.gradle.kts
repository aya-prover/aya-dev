// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.apache.tools.ant.taskdefs.condition.Os
import org.aya.gradle.CommonTasks
val mainClassQName = "org.aya.cli.console.Main"
CommonTasks.fatJar(project, mainClassQName)
application.mainClass.set(mainClassQName)
CommonTasks.nativeImageConfig(project)

dependencies {
  api(project(":tools-repl"))
  implementation(project(":cli-impl"))
  implementation(libs.picocli.runtime)
  annotationProcessor(libs.picocli.codegen)
  implementation(libs.jline.terminal.jansi)
  implementation(libs.jline.builtins)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.hamcrest)
  testImplementation(libs.jimgui.core)
}

plugins {
  id("org.graalvm.buildtools.native")
  application
}

tasks.withType<AbstractCopyTask>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val isMac = Os.isFamily(Os.FAMILY_MAC)
if (isMac) tasks.withType<JavaExec>().configureEach {
  jvmArgs("-XstartOnFirstThread")
}

tasks.withType<JavaCompile>().configureEach { CommonTasks.picocli(this) }

graalvmNative {
  binaries {
    named("main") {
      imageName.set("aya")
      mainClass.set(mainClassQName)
      debug.set(System.getenv("CI") == null)
    }
  }
  CommonTasks.nativeImageBinaries(
    project, javaToolchains, this,
    true,
    true
  )
}

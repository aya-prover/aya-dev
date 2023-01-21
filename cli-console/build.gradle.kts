// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.apache.tools.ant.taskdefs.condition.Os
import org.aya.gradle.CommonTasks
val mainClassQName = "org.aya.cli.console.Main"
CommonTasks.fatJar(project, mainClassQName)
application.mainClass.set(mainClassQName)
CommonTasks.nativeImageConfig(project)

dependencies {
  implementation(project(":cli-impl"))
  implementation(project(":tools-repl"))
  val deps: java.util.Properties by rootProject.ext
  api("info.picocli", "picocli", version = deps.getProperty("version.picocli"))
  annotationProcessor("info.picocli", "picocli-codegen", version = deps.getProperty("version.picocli"))
  val jlineVersion = deps.getProperty("version.jline")
  implementation("org.jline", "jline-terminal-jansi", version = jlineVersion)
  implementation("org.jline", "jline-builtins", version = jlineVersion)
  testImplementation("org.junit.jupiter", "junit-jupiter", version = deps.getProperty("version.junit"))
  testImplementation("org.hamcrest", "hamcrest", version = deps.getProperty("version.hamcrest"))
  testImplementation("org.ice1000.jimgui", "core", version = deps.getProperty("version.jimgui"))
  // testImplementation("org.ice1000.jimgui", "fun", version = deps.getProperty("version.jimgui"))
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

// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.apache.tools.ant.taskdefs.condition.Os
import org.aya.gradle.CommonTasks
CommonTasks.fatJar(project, "org.aya.cli.Main")
CommonTasks.nativeImageConfig(project)

dependencies {
  api(project(":base"))
  api(project(":parser"))
  api(project(":tools-repl"))
  val deps: java.util.Properties by rootProject.ext
  api("com.google.code.gson", "gson", version = deps.getProperty("version.gson"))
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
      mainClass.set("org.aya.cli.Main")
      debug.set(System.getenv("CI") == null)
      useFatJar.set(true)
    }
  }
  CommonTasks.nativeImageBinaries(
    project, javaToolchains, this,
    true,
    true
  )
}

tasks.named<org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask>("nativeCompile") {
  dependsOn(tasks.named("fatJar"))
  classpathJar.set(file("build/libs/cli-${project.version}-fat.jar"))
}

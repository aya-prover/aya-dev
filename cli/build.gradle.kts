// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.apache.tools.ant.taskdefs.condition.Os
import org.aya.gradle.CommonTasks
CommonTasks.fatJar(project, "org.aya.cli.Main")

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
  id("org.graalvm.buildtools.native") version "0.9.11"
}

tasks.withType<AbstractCopyTask>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val isMac = Os.isFamily(Os.FAMILY_MAC)
if (isMac) tasks.withType<JavaExec>().configureEach {
  jvmArgs("-XstartOnFirstThread")
}

tasks.withType<JavaCompile>().configureEach { CommonTasks.picocli(this) }

val genDir = file("build/native-config")
val configFile = file("reflect-config.txt")

val generateReflectionConfig = tasks.register<org.aya.gradle.GenerateReflectionConfigTask>("generateReflectionConfig") {
  outputDir = genDir
  inputFile = configFile
}

graalvmNative {
  binaries {
    named("main") {
      imageName.set("aya")
      mainClass.set("org.aya.cli.Main")
      verbose.set(true)
      fallback.set(false)
      debug.set(System.getenv("CI") == null)
      sharedLibrary.set(false)
      configurationFileDirectories.from(genDir)
      useFatJar.set(true)
      buildArgs.add("--report-unsupported-elements-at-runtime")
    }
  }

  binaries.configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(17))
      vendor.set(JvmVendorSpec.matching("GraalVM Community"))
    })
  }
}

tasks.named<org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask>("nativeCompile") {
  dependsOn(generateReflectionConfig)
  dependsOn(tasks.named("fatJar"))
  classpathJar.set(file("build/libs/cli-${project.version}-fat-no-preview.jar"))
}

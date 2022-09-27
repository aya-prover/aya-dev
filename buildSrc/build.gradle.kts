// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import java.util.*

plugins {
  java
  groovy
  antlr
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

val rootDir = projectDir.parentFile!!
val parserDir = rootDir.resolve("parser")
val genDir = parserDir.resolve("src/main/java")
val parserPackageName = "org.aya.parser"
val parserLibDir = genDir.resolve(parserPackageName.replace('.', '/')).absoluteFile

val copyModuleInfo = tasks.register<Copy>("copyModuleInfo") {
  group = "build setup"
  from(parserDir.resolve("module-info.java"))
  into(genDir)
}

tasks.withType<AntlrTask>().configureEach antlr@{
  outputDirectory = genDir
  copyModuleInfo.get().dependsOn(this@antlr)
  doFirst { parserLibDir.mkdirs() }
  arguments.addAll(
    listOf(
      "-package", parserPackageName,
      "-no-listener",
      "-lib", "$parserLibDir",
    ),
  )
}

tasks.named("build").configure {
  dependsOn(copyModuleInfo)
}

dependencies {
  val deps = Properties()
  deps.load(rootDir.resolve("gradle/deps.properties").reader())
  antlr("org.antlr", "antlr4", deps.getProperty("version.antlr"))
  api("org.aya-prover.upstream", "build-util", deps.getProperty("version.aya-upstream"))

  // The following is required for
  // - extracting common parts inside `graalvmNative` block
  // - specifying the plugin version from deps.properties
  implementation("org.graalvm.buildtools.native", "org.graalvm.buildtools.native.gradle.plugin", deps.getProperty("version.graalBuildTools"))
}

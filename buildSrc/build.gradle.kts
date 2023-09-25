// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import java.util.*

plugins {
  java
  groovy
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

val rootDir = projectDir.parentFile!!
val parserDir = rootDir.resolve("parser")
val genDir = parserDir.resolve("src/main/java")

val copyModuleInfo = tasks.register<Copy>("copyModuleInfo") {
  group = "build setup"
  from(parserDir.resolve("module-info.java"))
  into(genDir)
}

tasks.named("build").configure {
  dependsOn(copyModuleInfo)
}

dependencies {
  api(libs.aya.build.util)
  api(libs.aya.build.jflex)

  // The following is required for
  // - extracting common parts inside `graalvmNative` block
  // - specifying the plugin version from libs.versions.toml
  implementation(libs.graal.nitools)
}

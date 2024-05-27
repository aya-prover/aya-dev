// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
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
}

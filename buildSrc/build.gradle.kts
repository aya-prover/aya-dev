// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
import java.util.*

plugins {
  java
  groovy
  antlr
}

repositories { jcenter() }

val rootDir = projectDir.parentFile!!
val parserDir = rootDir.resolve("parser")
val genDir = parserDir.resolve("src/main/java")
val copyModuleInfo = tasks.register<Copy>("copyModuleInfo") {
  group = "build setup"
  from(parserDir.resolve("module-info.java"))
  into(genDir)
}

tasks.withType<AntlrTask>().configureEach {
  dependsOn(copyModuleInfo)
  outputDirectory = genDir
  arguments.addAll(listOf(
    "-package", "org.mzi.parser",
    "-no-listener",
    "-visitor"
  ))
}

dependencies {
  val deps = Properties()
  deps.load(rootDir.resolve("gradle/deps.properties").reader())
  antlr("org.antlr", "antlr4", deps.getProperty("version.antlr"))
}

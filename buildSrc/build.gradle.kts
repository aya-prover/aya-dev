// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import java.util.*

plugins {
  java
  groovy
  antlr
}

repositories { mavenCentral() }

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
}

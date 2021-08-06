// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
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

val copyModuleInfo = tasks.register<Copy>("copyModuleInfo") {
  group = "build setup"
  from(parserDir.resolve("module-info.java"))
  into(genDir)
}

tasks.withType<AntlrTask>().configureEach antlr@{
  outputDirectory = genDir
  copyModuleInfo.get().dependsOn(this@antlr)
  val packageName = "org.aya.parser"
  val libPath = genDir.resolve(packageName.replace('.', '/')).absoluteFile
  doFirst { libPath.mkdirs() }
  arguments.addAll(
    listOf(
      "-package", packageName,
      "-no-listener",
      "-lib", "$libPath",
    ),
  )
}

tasks.named("build").configure { dependsOn(copyModuleInfo) }

dependencies {
  val deps = Properties()
  deps.load(rootDir.resolve("gradle/deps.properties").reader())
  antlr("org.antlr", "antlr4", deps.getProperty("version.antlr"))
}

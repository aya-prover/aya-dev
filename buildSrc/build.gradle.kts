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
val genDir = rootDir.resolve("base/src/main/gen")

tasks.withType<AntlrTask>().configureEach antlr@{
  outputDirectory = genDir
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

dependencies {
  val deps = Properties()
  deps.load(rootDir.resolve("gradle/deps.properties").reader())
  antlr("org.antlr", "antlr4", deps.getProperty("version.antlr"))
}

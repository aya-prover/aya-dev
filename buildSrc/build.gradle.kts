// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
plugins {
  java
  groovy
  antlr
}

repositories { jcenter() }

tasks.withType<AntlrTask>().configureEach {
  outputDirectory = projectDir.parentFile.resolve("parser/src/main/java")
  arguments.addAll(listOf(
    "-package", "org.mzi.parser",
    "-no-listener",
    "-visitor"
  ))
}

dependencies {
  antlr("org.antlr:antlr4:4.8")
}

// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
val genDir = file("src/main/gen")
idea.module.generatedSourceDirs.add(genDir)
sourceSets.main {
  java.srcDirs(genDir)
}

dependencies {
  api(libs.annotations)
  api(libs.aya.ij.core)
  api(libs.aya.ij.wrapper)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.hamcrest) }

val lexer = tasks.register<org.aya.gradle.JFlexTask>("lexer") {
  outputDir = genDir.resolve("org/aya/parser")
  jflex = file("src/main/grammar/AyaPsiLexer.flex")
}

listOf(tasks.compileJava, tasks.sourcesJar).forEach {
  it.configure { dependsOn(lexer) }
}

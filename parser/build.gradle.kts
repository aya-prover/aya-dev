// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
val genDir = file("src/main/gen")
idea.module.generatedSourceDirs.add(genDir)
sourceSets.main {
  java.srcDirs(genDir)
}

dependencies {
  val deps: java.util.Properties by rootProject.ext
  api("org.jetbrains", "annotations", version = deps.getProperty("version.annotations"))
  api("org.aya-prover.upstream", "ij-parsing-core", version = deps.getProperty("version.aya-upstream"))
  testImplementation("org.junit.jupiter", "junit-jupiter", version = deps.getProperty("version.junit"))
  testImplementation("org.hamcrest", "hamcrest", version = deps.getProperty("version.hamcrest"))
}

val lexer = tasks.register<org.aya.gradle.JFlexTask>("lexer") {
  outputDir = genDir.resolve("org/aya/parser")
  val grammar = file("src/main/grammar")
  jflex = grammar.resolve("AyaPsiLexer.flex")
  skel = grammar.resolve("aya-flex.skeleton")
}

listOf(tasks.compileJava, tasks.sourcesJar).forEach {
  it.configure { dependsOn(lexer) }
}

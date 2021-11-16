// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
dependencies {
  val deps: java.util.Properties by rootProject.ext
  api("org.antlr", "antlr4-runtime", version = deps.getProperty("version.antlr"))
}

val genDir = file("src/main/java")
val ymlGenDir = file("src/main/yml")
val rootDir = projectDir.parentFile!!
val buildSrcDir = rootDir.resolve("buildSrc")

val generateLexerToken = tasks.register<org.aya.gradle.GenerateLexerTokenTask>("generateLexerToken") {
  basePackage = "org.aya.parser"
  outputDir = genDir.resolve("org/aya/parser")
  ymlOutputDir = ymlGenDir
  lexerG4 = buildSrcDir.resolve("src/main/antlr/org/aya/parser/AyaLexer.g4")
}

tasks.compileJava { dependsOn(generateLexerToken) }
tasks.sourcesJar { dependsOn(generateLexerToken) }

//idea.module.generatedSourceDirs.add(genDir)
tasks.register("cleanSource") {
  group = "build"
  genDir.deleteRecursively()
  ymlGenDir.deleteRecursively()
}


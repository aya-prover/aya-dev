// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE file.
dependencies {
  api(project(":api"))
  api(project(":tools"))
  api(project(":pretty"))
  api(project(":parser"))
  val deps: java.util.Properties by rootProject.ext
  implementation("org.aya-prover", "commonmark", version = deps.getProperty("version.commonmark"))
  testImplementation("org.junit.jupiter", "junit-jupiter", version = deps.getProperty("version.junit"))
  testImplementation("org.hamcrest", "hamcrest", version = deps.getProperty("version.hamcrest"))
  testImplementation(project(":cli"))
}

val genDir = file("src/main/gen")
val generateVersion = tasks.register<org.aya.gradle.GenerateVersionTask>("generateVersion") {
  basePackage = "org.aya"
  outputDir = genDir.resolve("org/aya/prelude")
}

idea.module.generatedSourceDirs.add(genDir)
sourceSets.main {
  java.srcDirs(genDir)
}

tasks.compileJava { dependsOn(generateVersion) }
tasks.sourcesJar { dependsOn(generateVersion) }

val cleanGenerated = tasks.register("cleanGenerated") {
  group = "build"
  genDir.deleteRecursively()
}

tasks.named("clean") { dependsOn(cleanGenerated) }

tasks.named<Test>("test") {
  testLogging.showStandardStreams = true
  testLogging.showCauses = true
  inputs.dir(projectDir.resolve("src/test/resources"))
}

tasks.register<JavaExec>("runCustomTest") {
  group = "Execution"
  classpath = sourceSets.test.get().runtimeClasspath
  main = "org.aya.test.TestRunner"
}

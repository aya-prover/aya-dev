import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.gradle.CommonTasks
CommonTasks.nativeImageConfig(project)

dependencies {
  api(project(":tools"))
  api(project(":pretty"))
  val deps: java.util.Properties by rootProject.ext
  implementation("org.aya-prover", "commonmark", version = deps.getProperty("version.commonmark"))
  testImplementation("org.junit.jupiter", "junit-jupiter", version = deps.getProperty("version.junit"))
  testImplementation("org.hamcrest", "hamcrest", version = deps.getProperty("version.hamcrest"))
  testImplementation(project(":cli"))
  // benchmark
  testAnnotationProcessor("org.openjdk.jmh", "jmh-generator-annprocess", version = deps.getProperty("version.jmh"))
  testImplementation("org.openjdk.jmh", "jmh-core", version = deps.getProperty("version.jmh"))
}

plugins {
  id("org.graalvm.buildtools.native")
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
  mainClass.set("org.aya.test.TestRunner")
}

graalvmNative {
  CommonTasks.nativeImageBinaries(
    project, javaToolchains, this,
    false,
    true
  )
}

tasks.register<JavaExec>("bench") {
  description = "Executes JMH micro-benchmarking"
  group = "Execution"
  mainClass.set("org.aya.test.bench.ListBench")
  classpath = sourceSets.test.get().runtimeClasspath

  val time = DateTimeFormatter.ofPattern("yyyy-LLL-dd-hh-mm-ss").format(LocalDateTime.now())
  val resultFile = file("${project.buildDir}/bench/report${time}.txt").absoluteFile

  // doesn't need this actually
  val inputFiles = file("src/test/resources/bench").listFiles()?.map { it.absoluteFile }
  args(resultFile)
  if (inputFiles != null) {
    args(inputFiles)
  }

  // Currently there is only one benchmark. But this should actually be only one of
  // the many benchmarks grouped in a special task
  workingDir = file("src/test/resources/bench")

  doFirst {
    resultFile.parentFile.mkdirs()
  }
}

tasks.register<JavaExec>("jmhHelp") {
  description = "Prints the available command line options for JMH."
  mainClass.set("org.openjdk.jmh.Main")
  group = "help"
  classpath = sourceSets.test.get().runtimeClasspath
  args("-h")
}

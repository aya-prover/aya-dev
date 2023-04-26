// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.gradle.CommonTasks
CommonTasks.nativeImageConfig(project)

dependencies {
  api(project(":tools"))
  api(project(":tools-md"))
  api(project(":pretty"))
  val deps: java.util.Properties by rootProject.ext
  api("org.aya-prover.guest0x0", "cubical", version = deps.getProperty("version.guest0x0"))
  implementation("org.aya-prover", "commonmark", version = deps.getProperty("version.commonmark"))
  implementation("org.aya-prover.upstream", "ij-parsing-core", version = deps.getProperty("version.aya-upstream"))
  testImplementation("org.junit.jupiter", "junit-jupiter", version = deps.getProperty("version.junit"))
  testImplementation("org.junit.jupiter", "junit-jupiter-params", version = deps.getProperty("version.junit"))
  testImplementation("org.hamcrest", "hamcrest", version = deps.getProperty("version.hamcrest"))
  testImplementation(project(":cli-impl"))
  testImplementation(project(":ide"))
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
tasks.withType<org.aya.gradle.GenerateReflectionConfigTask>().configureEach {
  extraDir = file("src/main/java/org/aya/core/serde")
  classPrefixes = listOf("SerTerm", "SerPat", "SerDef", "CompiledAya")
  excludeNamesSuffix = listOf("SerTerm\$DeState", "CompiledAya\$CompiledAya", "CompiledAya\$Serialization")
  packageName = "org.aya.core.serde"
}

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

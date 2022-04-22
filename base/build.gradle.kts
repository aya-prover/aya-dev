// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
dependencies {
  api(project(":tools"))
  api(project(":pretty"))
  val deps: java.util.Properties by rootProject.ext
  implementation("org.aya-prover", "commonmark", version = deps.getProperty("version.commonmark"))
  testImplementation("org.junit.jupiter", "junit-jupiter", version = deps.getProperty("version.junit"))
  testImplementation("org.hamcrest", "hamcrest", version = deps.getProperty("version.hamcrest"))
  testImplementation(project(":cli"))
}

plugins {
  id("org.graalvm.buildtools.native") version "0.9.11"
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

val configGenDir = file("build/native-config")
val configTemplateFile = file("reflect-config.txt")

val generateReflectionConfig = tasks.register<org.aya.gradle.GenerateReflectionConfigTask>("generateReflectionConfig") {
  outputDir = configGenDir
  inputFile = configTemplateFile
}

graalvmNative {
  binaries {
    named("test") {
    }
  }

  binaries.configureEach {
    fallback.set(false)
    verbose.set(true)
    sharedLibrary.set(false)
    configurationFileDirectories.from(configGenDir)
    buildArgs.add("--report-unsupported-elements-at-runtime")

    javaLauncher.set(javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(17))
      vendor.set(JvmVendorSpec.matching("GraalVM Community"))
    })
  }
}

tasks.named<org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask>("nativeCompile") {
  // native compiling base module is meaningless, just disable it
  this.enabled = false
}

tasks.named<org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask>("nativeTestCompile") {
  dependsOn(generateReflectionConfig)
}

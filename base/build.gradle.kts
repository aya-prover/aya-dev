// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.gradle.GenerateVersionTask

dependencies {
  api(project(":syntax"))
  api(project(":tools-md"))
  // implementation(libs.manifold.delegate.runtime)
  // annotationProcessor(libs.manifold.delegate.codegen)
  testImplementation(project(":producer"))
  testImplementation(project(":jit-compiler"))
  // testAnnotationProcessor(libs.manifold.delegate.codegen)
}

val genDir = file("src/main/gen")
var javaVersion: Int by rootProject.ext

val generateVersion = tasks.register<GenerateVersionTask>("generateVersion") {
  basePackage = "org.aya"
  outputDir = genDir.resolve("org/aya/prelude")
  jdkVersion = javaVersion
}

idea.module.generatedSourceDirs.add(genDir)
sourceSets.main { java.srcDirs(genDir) }

tasks.compileJava { dependsOn(generateVersion) }
tasks.sourcesJar { dependsOn(generateVersion) }

val cleanGenerated = tasks.register("cleanGenerated") {
  group = "build"
  genDir.deleteRecursively()
}

tasks.named("clean") { dependsOn(cleanGenerated) }

// Thank you Long
tasks.withType<JavaExec>().configureEach { jvmArgs( "-Xss32m") }

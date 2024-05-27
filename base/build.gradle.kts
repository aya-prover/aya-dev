// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.gradle.GenerateVersionTask

dependencies {
  api(project(":syntax"))
  api(project(":tools-md"))
  // implementation(libs.manifold.delegate.runtime)
  // annotationProcessor(libs.manifold.delegate.codegen)
  testImplementation(project(":producer"))
  testImplementation(libs.junit.params)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.hamcrest)
  // testAnnotationProcessor(libs.manifold.delegate.codegen)
}

val genDir = file("src/main/gen")
val generateVersion = tasks.register<GenerateVersionTask>("generateVersion") {
  basePackage = "org.aya"
  outputDir = genDir.resolve("org/aya/prelude")
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

// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.gradle.CommonTasks

dependencies {
  api(project(":base"))
  api(project(":parser"))
  api(libs.gson)
  implementation(libs.sourcebuddy)
  implementation(project(":producer"))
  implementation(project(":jit-compiler"))
  implementation(libs.commonmark)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.hamcrest)
  testImplementation(project(":cli-console"))
}

tasks.named<Test>("test") {
  testLogging.showStandardStreams = true
  testLogging.showCauses = true
  val resources = projectDir.resolve("src/test/resources")
  resources.mkdirs()
  inputs.dir(resources)
}

tasks.register<JavaExec>("runCustomTest") {
  group = "Execution"
  classpath = sourceSets.test.get().runtimeClasspath
  mainClass.set("org.aya.test.TestRunner")
}

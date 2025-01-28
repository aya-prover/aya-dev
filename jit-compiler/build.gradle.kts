// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

dependencies {
  api(project(":base"))
  implementation("org.glavo:classfile:0.5.0")
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.hamcrest)
  testImplementation(project(":producer"))
}

tasks.withType<JavaExec>().configureEach {
  val vmArgs = jvmArgs ?: mutableListOf()
  vmArgs.add("-Xss32M")
  jvmArgs = vmArgs
}

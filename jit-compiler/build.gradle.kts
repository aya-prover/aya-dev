// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

dependencies {
  api(project(":base"))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.hamcrest)
  testImplementation(project(":producer"))
}

tasks.withType<JavaExec>().configureEach {
  val vmArgs = jvmArgs ?: mutableListOf()
  vmArgs.add("-Xss32M")
  jvmArgs = vmArgs
}

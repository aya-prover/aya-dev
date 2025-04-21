// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

dependencies {
  api(project(":base"))
  implementation("org.glavo:classfile:0.5.0")
  testImplementation(project(":producer"))
}

tasks.withType<JavaExec>().configureEach { jvmArgs("-Xss32M") }

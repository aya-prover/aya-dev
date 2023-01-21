// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

dependencies {
  api(project(":base"))
  api(project(":parser"))
  val deps: java.util.Properties by rootProject.ext
  api("com.google.code.gson", "gson", version = deps.getProperty("version.gson"))
  implementation("org.aya-prover", "commonmark", version = deps.getProperty("version.commonmark"))
  testImplementation("org.junit.jupiter", "junit-jupiter", version = deps.getProperty("version.junit"))
  testImplementation("org.hamcrest", "hamcrest", version = deps.getProperty("version.hamcrest"))
  testImplementation("org.ice1000.jimgui", "core", version = deps.getProperty("version.jimgui"))
  // testImplementation("org.ice1000.jimgui", "fun", version = deps.getProperty("version.jimgui"))
}
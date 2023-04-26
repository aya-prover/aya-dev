// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
dependencies {
  val deps: java.util.Properties by rootProject.ext
  api("org.jetbrains", "annotations", version = deps.getProperty("version.annotations"))
  api("org.glavo.kala", "kala-common", version = deps.getProperty("version.kala"))
  api(project(":pretty"))
  implementation("org.aya-prover.upstream", "ij-util-text", version = deps.getProperty("version.aya-upstream"))
  testImplementation("org.junit.jupiter", "junit-jupiter", version = deps.getProperty("version.junit"))
  testImplementation("org.hamcrest", "hamcrest", version = deps.getProperty("version.hamcrest"))
}

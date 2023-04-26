// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
dependencies {
  val deps: java.util.Properties by rootProject.ext

  api(project(":tools"))
  api("org.jetbrains", "annotations", version = deps.getProperty("version.annotations"))
  implementation("org.aya-prover", "commonmark", version = deps.getProperty("version.commonmark"))
}

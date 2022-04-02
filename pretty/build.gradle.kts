// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE file.
dependencies {
  val deps: java.util.Properties by rootProject.ext
  api("org.jetbrains", "annotations", version = deps.getProperty("version.annotations"))
  api("org.glavo.kala", "kala-common", version = deps.getProperty("version.kala"))
  testImplementation("org.junit.jupiter", "junit-jupiter", version = deps.getProperty("version.junit"))
  testImplementation("org.hamcrest", "hamcrest", version = deps.getProperty("version.hamcrest"))
}

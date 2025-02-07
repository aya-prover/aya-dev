// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
dependencies {
  api(libs.annotations)
  api(libs.kala.common)
  api(project(":pretty"))
  api(libs.aya.ij.text)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.hamcrest)
}

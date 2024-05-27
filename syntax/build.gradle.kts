// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

//CommonTasks.nativeImageConfig(project)

dependencies {
  api(project(":tools-kala"))
  api(project(":tools-md"))
  api(project(":pretty"))
  api(libs.aya.ij.core)
  testImplementation(libs.junit.params)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.hamcrest)
}

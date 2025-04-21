// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import org.aya.gradle.CommonTasks

CommonTasks.fatJar(project, "N/A")

dependencies {
  api(project(":tools-kala"))
  api(project(":tools-md"))
  api(project(":pretty"))
  api(libs.aya.ij.core)
}

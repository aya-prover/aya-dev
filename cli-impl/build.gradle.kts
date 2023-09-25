// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.gradle.CommonTasks
CommonTasks.nativeImageConfig(project)

dependencies {
  api(project(":base"))
  api(project(":parser"))
  api(libs.gson)
  implementation(libs.aya.commonmark)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.hamcrest)
  testImplementation(project(":cli-console"))
}

// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
dependencies {
  val deps: java.util.Properties by rootProject.ext
  api("org.junit.jupiter", "junit-jupiter", version = deps.getProperty("version.junit"))
  api("org.hamcrest", "hamcrest", version = deps.getProperty("version.hamcrest"))
  api(project(":base"))
  implementation(project(":parser"))
  implementation(project(":pretty"))
}

// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
dependencies {
  val deps: java.util.Properties by rootProject.ext
  api("com.beust", "jcommander", version = deps.getProperty("version.jcommander"))
  implementation(project(":base"))
  implementation(project(":parser"))
  implementation(project(":pretty"))
}

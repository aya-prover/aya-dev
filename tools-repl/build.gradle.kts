// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
dependencies {
  api(project(":pretty"))
  api(project(":tools"))
  val deps: java.util.Properties by rootProject.ext
  val jlineVersion = deps.getProperty("version.jline")
  api("org.jline", "jline-reader", version = jlineVersion)
  api("org.jline", "jline-terminal", version = jlineVersion)
}

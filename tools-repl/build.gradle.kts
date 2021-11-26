// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE file.
dependencies {
  api(project(":pretty"))
  val deps: java.util.Properties by rootProject.ext
  val jlineVersion = deps.getProperty("version.jline")
  api("org.jline", "jline-reader", version = jlineVersion)
  api("org.jline", "jline-terminal", version = jlineVersion)
  api("org.antlr", "antlr4-runtime", version = deps.getProperty("version.antlr"))
}

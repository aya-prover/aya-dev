// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
dependencies {
  val antlrVersion: String by rootProject.ext
  api("org.antlr:antlr4-runtime:$antlrVersion")
}

idea {
  module.generatedSourceDirs.add(file("src/main/java"))
}

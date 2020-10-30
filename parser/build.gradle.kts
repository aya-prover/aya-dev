// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
dependencies {
  val deps: java.util.Properties by rootProject.ext
  api("org.antlr:antlr4-runtime:${deps.getProperty("version.antlr")}")
}

idea {
  module.generatedSourceDirs.add(file("src/main/java"))
}

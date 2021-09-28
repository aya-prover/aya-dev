// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
dependencies {
  val deps: java.util.Properties by rootProject.ext
  api("org.antlr", "antlr4-runtime", version = deps.getProperty("version.antlr"))
}

val genDir = file("src/main/java")

idea.module.generatedSourceDirs.add(genDir)
tasks.register("cleanSource") {
  group = "build"
  genDir.deleteRecursively()
}


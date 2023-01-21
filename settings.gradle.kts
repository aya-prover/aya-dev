// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

rootProject.name = "aya-prover"

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage") repositories {
    mavenCentral()
  }
}

include(
  "cli",
  "tools",
  "tools-repl",
  "base",
  "pretty",
  "parser",
  "ide",
  "ide-lsp",
)

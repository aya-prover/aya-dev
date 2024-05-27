// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

rootProject.name = "aya-prover"

@Suppress("UnstableApiUsage") dependencyResolutionManagement.repositories {
  mavenCentral()
}

include(
  "cli-impl",
  "cli-console",
  "tools",
  // Uses kala-primitives
  "jit-compiler",
  "tools-kala",
  "tools-md",
  "tools-repl",
  "syntax",
  "base",
  "pretty",
  "parser",
  "producer",
  "ide",
  "ide-lsp",
)

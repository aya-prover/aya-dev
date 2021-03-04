// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.

rootProject.name = "aya-prover"

dependencyResolutionManagement {
  repositories {
    jcenter()
    mavenCentral()
    maven(url = "https://jitpack.io")
  }
}

include(
  "api",
  "tester",
  "docs",
  "cli",
  // "proto",
  "parser",
  "base",
  "pretty"
)

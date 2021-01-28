// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.

rootProject.name = "mzi"

dependencyResolutionManagement {
  repositories {
    jcenter()
    mavenCentral()
    maven(url = "https://dl.bintray.com/glavo/maven")
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

// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
dependencies {
  val annotationsVersion: String by rootProject.ext
  val kalaVersion: String by rootProject.ext
  api("org.jetbrains:annotations:$annotationsVersion")
  api("asia.kala:kala-common:$kalaVersion")
}

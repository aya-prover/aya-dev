// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
dependencies {
  val deps: java.util.Properties by rootProject.ext
  api("org.jetbrains:annotations:${deps.getProperty("version.annotations")}")
  api("asia.kala:kala-common:${deps.getProperty("version.kala")}")
}

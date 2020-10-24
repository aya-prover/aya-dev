// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.

tasks.register<org.mzi.gradle.PreprocessZhihuTask>("zhihu") {
  from(file("src"))
  into(file("zhihu"))
}

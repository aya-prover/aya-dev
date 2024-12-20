// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.collection.Seq;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VersionTest {
  @Test public void test() {
    var versions = Seq.of(
      Version.create("2.1.0"),
      Version.create("2.2"),
      new Version(3, 3, 3),
      new Version("2", "4", "3")
    ).sorted();
    assertEquals("2.1", versions.get(0).toString());
    assertEquals("2.1.0", versions.get(0).getLongString());
    assertEquals("2.2", versions.get(1).toString());
    assertEquals("3.3.3", versions.getLast().toString());
  }
}

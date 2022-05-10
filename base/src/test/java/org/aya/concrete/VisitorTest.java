// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import org.aya.util.error.Global;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class VisitorTest {
  @BeforeAll public static void enableTest() {
    Global.NO_RANDOM_NAME = true;
    Global.UNITE_SOURCE_POS = true;
  }

  @AfterAll public static void exit() {
    Global.reset();
  }
}

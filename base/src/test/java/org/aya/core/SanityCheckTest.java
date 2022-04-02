// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import org.aya.core.visitor.Subst;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SanityCheckTest {
  @Test public void substEmpty() {
    assertTrue(Subst.EMPTY.isEmpty());
  }
}

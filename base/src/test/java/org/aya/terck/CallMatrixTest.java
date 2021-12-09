// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CallMatrixTest {
  @Test
  public void mul() {
    var m1 = new CallMatrix<>(2, 2, "domain1", "codomain1");
    var m2 = new CallMatrix<>(2, 2, "domain2", "codomain2");
    var m = m1.mul(m2);
    assertEquals("domain1", m.caller());
    assertEquals("codomain2", m.callee());
  }
}

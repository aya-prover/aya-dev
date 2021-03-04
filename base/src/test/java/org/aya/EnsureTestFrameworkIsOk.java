// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya;

import org.aya.test.Lisp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class EnsureTestFrameworkIsOk {
  @Test
  public void ensureParseErrorReported() {
    assertThrows(NullPointerException.class, () -> Lisp.parse("("));
    assertThrows(NullPointerException.class, () -> Lisp.parse(")"));
    assertThrows(Exception.class, () -> Lisp.parse("233"));
    assertThrows(IndexOutOfBoundsException.class, () -> Lisp.parse("(Pi )"));
  }
}

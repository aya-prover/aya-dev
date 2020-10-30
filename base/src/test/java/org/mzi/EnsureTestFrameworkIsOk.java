// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi;

import org.junit.jupiter.api.Test;
import org.mzi.test.Lisp;

import static org.junit.jupiter.api.Assertions.*;

public class EnsureTestFrameworkIsOk {
  @Test
  public void ensureParseErrorReported() {
    assertThrows(NullPointerException.class, () -> Lisp.reallyParse("("));
    assertThrows(NullPointerException.class, () -> Lisp.reallyParse(")"));
    assertThrows(Exception.class, () -> Lisp.reallyParse("233"));
  }
}

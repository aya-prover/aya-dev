// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core;

import org.junit.jupiter.api.Test;
import org.mzi.test.Lisp;

import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

public class TeleTest {
  @Test
  public void biTelescope() {
    var tele = Lisp.reallyParseTele("(x (y ren ex null))", new TreeMap<>());
    assertTrue(tele.explicit());
    assertNotNull(tele.next());
    assertEquals(tele.next(), tele.last());
    assertEquals(tele.next().type(), tele.type());
    assertTrue(tele.next().explicit());
    assertEquals(2, tele.size());
  }

  @Test
  public void uniTelescope() {
    var tele = Lisp.reallyParseTele("(xy ren ex null)", new TreeMap<>());
    assertTrue(tele.explicit());
    assertNull(tele.next());
    assertEquals(tele, tele.last());
    assertEquals(1, tele.size());
  }
}

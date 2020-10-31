// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core;

import org.junit.jupiter.api.Test;
import org.mzi.test.Lisp;
import org.mzi.test.LispTestCase;

import static org.junit.jupiter.api.Assertions.*;

public class TeleTest extends LispTestCase {
  @Test
  public void biTelescope() {
    var tele = Lisp.reallyParseTele("(x (y ren ex null))", vars);
    assertTrue(tele.explicit());
    assertNotNull(tele.next());
    assertEquals(tele.next(), tele.last());
    assertEquals(tele.next().type(), tele.type());
    assertTrue(tele.next().explicit());
    assertEquals(2, tele.size());
  }

  @Test
  public void dropTelescope() {
    var term = Lisp.reallyParse("(Pi (a (U) ex (b (U) ex null)) a)");
    assertEquals(term, term.dropTeleDT(0));
    assertNotNull(term.dropTeleDT(1));
    assertNotEquals(term, term.dropTeleDT(1));
    assertNotNull(term.dropTeleDT(2));
    assertNotEquals(term, term.dropTeleDT(2));
    assertNull(term.dropTeleDT(3));
  }

  @Test
  public void dropLamTelescope() {
    var term = Lisp.reallyParse("(lam (a (U) ex (b (U) ex null)) a)");
    assertEquals(term, term.dropTeleLam(0));
    assertNotNull(term.dropTeleLam(1));
    assertNotEquals(term, term.dropTeleLam(1));
    assertNotNull(term.dropTeleLam(2));
    assertNotEquals(term, term.dropTeleLam(2));
    assertNull(term.dropTeleLam(3));
  }

  @Test
  public void uniTelescope() {
    var tele = Lisp.reallyParseTele("(xy ren ex null)", vars);
    assertTrue(tele.explicit());
    assertNull(tele.next());
    assertEquals(tele, tele.last());
    assertEquals(1, tele.size());
  }
}

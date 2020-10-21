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

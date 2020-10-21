package org.mzi.core;

import org.junit.jupiter.api.Test;
import org.mzi.api.util.NormalizeMode;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.LamTerm;
import org.mzi.core.term.RefTerm;
import org.mzi.core.term.Term;
import org.mzi.test.Lisp;

import static org.junit.jupiter.api.Assertions.*;

public class NormalizeTest {
  @Test
  public void noNormalizeNeutral() {
    var term = Lisp.reallyParse("(app f a)");
    assertEquals(term, term.normalize(NormalizeMode.NF));
    assertEquals(term, term.normalize(NormalizeMode.WHNF));
  }

  @Test
  public void noNormalizeCanonical() {
    var term = Lisp.reallyParse("(lam (a (U) ex null) a)");
    assertEquals(term, term.normalize(NormalizeMode.NF));
    assertEquals(term, term.normalize(NormalizeMode.WHNF));
  }

  @Test
  public void redexNormalize() {
    var term = Lisp.reallyParse("(app (lam (a (U) ex null) a) b)");
    assertTrue(term instanceof AppTerm);
    var whnf = term.normalize(NormalizeMode.WHNF);
    var nf = term.normalize(NormalizeMode.NF);
    assertTrue(whnf instanceof RefTerm);
    assertTrue(nf instanceof RefTerm);
    assertEquals(whnf, nf);
  }

  @Test
  public void whnfNoNormalize() {
    var term = Lisp.reallyParse("(lam (x (U) ex null) (app (lam (a (U) ex null) a) b))");
    assertEquals(term, term.normalize(NormalizeMode.WHNF));
  }

  @Test
  public void nfNormalizeCanonical() {
    var term = Lisp.reallyParse("(lam (x (U) ex null) (app (lam (a (U) ex null) a) b))");
    assertTrue(((LamTerm) term).body() instanceof AppTerm);
    var nf = term.normalize(NormalizeMode.NF);
    assertNotEquals(term, nf);
    assertTrue(((LamTerm) nf).body() instanceof RefTerm);
  }
}

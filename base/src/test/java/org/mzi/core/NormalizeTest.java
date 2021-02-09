// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.mzi.api.util.NormalizeMode;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.LamTerm;
import org.mzi.core.term.RefTerm;
import org.mzi.test.Lisp;
import org.mzi.test.LispTestCase;

import static org.junit.jupiter.api.Assertions.*;

public class NormalizeTest extends LispTestCase {
  private void unchanged(@Language("TEXT") String code) {
    var term = Lisp.parse(code);
    assertEquals(term, term.normalize(NormalizeMode.NF));
    assertEquals(term, term.normalize(NormalizeMode.WHNF));
  }

  @Test
  public void noNormalizeNeutral() {
    unchanged("(app f a)");
  }

  @Test
  public void noNormalizeCanonical() {
    unchanged("(lam (a (U) ex) a)");
    unchanged("(Pi (a (U) ex) a)");
    unchanged("(Sigma (a (U) ex null) a)");
  }

  @Test
  public void redexNormalize() {
    var term = Lisp.parse("(app (lam (a (U) ex) a) b)");
    assertTrue(term instanceof AppTerm);
    var whnf = term.normalize(NormalizeMode.WHNF);
    var nf = term.normalize(NormalizeMode.NF);
    assertTrue(whnf instanceof RefTerm);
    assertTrue(nf instanceof RefTerm);
    assertEquals(whnf, nf);
  }

  @Test
  public void whnfNoNormalize() {
    var term = Lisp.parse("(lam (x (U) ex) (app (lam (a (U) ex) a) b))");
    assertEquals(term, term.normalize(NormalizeMode.WHNF));
  }

  @Test
  public void nfNormalizeCanonical() {
    // \x : U. (\a : U. a) b
    var term = Lisp.parse("(lam (x (U) ex) (app (lam (a (U) ex) a) b))");
    assertTrue(((LamTerm) term).body() instanceof AppTerm);
    var nf = term.normalize(NormalizeMode.NF);
    assertNotEquals(term, nf);
    assertTrue(((LamTerm) nf).body() instanceof RefTerm);
  }

  @Test
  public void unfoldDef() {
    // (x y : U)
    var def = Lisp.parseDef("id",
      "(y (U) ex null)", "y", "y", vars);
    var term = Lisp.parse("(fncall id kiva)", vars);
    assertTrue(term instanceof AppTerm.FnCall);
    assertEquals("id", def.ref().name());
    assertEquals(1, def.telescope().size());
    var norm = term.normalize(NormalizeMode.WHNF);
    assertNotEquals(term, norm);
    assertEquals(new RefTerm(vars.get("kiva")), norm);
  }
}

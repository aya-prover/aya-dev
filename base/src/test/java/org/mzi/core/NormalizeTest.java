// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mzi.api.ref.Var;
import org.mzi.api.util.NormalizeMode;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.LamTerm;
import org.mzi.core.term.RefTerm;
import org.mzi.test.Lisp;

import java.util.Map;
import java.util.TreeMap;

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
    // \x : U. (\a : U. a) b
    var term = Lisp.reallyParse("(lam (x (U) ex null) (app (lam (a (U) ex null) a) b))");
    assertTrue(((LamTerm) term).body() instanceof AppTerm);
    var nf = term.normalize(NormalizeMode.NF);
    assertNotEquals(term, nf);
    assertTrue(((LamTerm) nf).body() instanceof RefTerm);
  }

  @Test
  public void unfoldDef() {
    // (x y : U)
    @NotNull Map<String, @NotNull Var> refs = new TreeMap<>();
    var def = Lisp.reallyParseDef("id",
      "(y (U) ex null)", "y", "y", refs);
    var term = Lisp.reallyParse("(fncall id kiva)", refs);
    assertTrue(term instanceof AppTerm.FnCall);
    assertEquals("id", def.ref.name());
    assertEquals(1, def.telescope.size());
    var norm = term.normalize(NormalizeMode.WHNF);
    assertNotEquals(term, norm);
    assertEquals(new RefTerm(refs.get("kiva")), norm);
  }
}

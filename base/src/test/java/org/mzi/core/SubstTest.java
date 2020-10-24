// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mzi.api.ref.Var;
import org.mzi.tyck.sort.Sort;
import org.mzi.core.subst.TermSubst;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.UnivTerm;
import org.mzi.test.Lisp;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

public class SubstTest {
  @Test
  public void emptySubst() {
    var term = Lisp.reallyParse("(app tony lambda)");
    assertTrue(term instanceof AppTerm);
    assertEquals(term, term.subst(TermSubst.EMPTY));
  }

  @Test
  public void unrelatedSubst() {
    var term = Lisp.reallyParse("(app beta lambda)");
    assertTrue(term instanceof AppTerm);
    assertEquals(term, term.subst(new TermSubst(() -> "lambda", new UnivTerm(Sort.SET0))));
  }

  @Test
  public void relatedSubst() {
    @NotNull Map<String, @NotNull Var> refs = new TreeMap<>();
    var term = Lisp.reallyParse("(app tony beta)", refs);
    assertTrue(term instanceof AppTerm);
    assertNotEquals(term, term.subst(new TermSubst(refs.get("beta"), new UnivTerm(Sort.SET0))));
  }
}

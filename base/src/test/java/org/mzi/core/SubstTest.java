// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core;

import org.junit.jupiter.api.Test;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.UnivTerm;
import org.mzi.core.visitor.Substituter;
import org.mzi.test.Lisp;
import org.mzi.test.LispTestCase;
import org.mzi.tyck.sort.Sort;

import static org.junit.jupiter.api.Assertions.*;

public class SubstTest extends LispTestCase {
  @Test
  public void emptySubst() {
    var term = Lisp.reallyParse("(app tony lambda)");
    assertTrue(term instanceof AppTerm);
    assertEquals(term, term.subst(Substituter.TermSubst.EMPTY));
  }

  @Test
  public void unrelatedSubst() {
    var term = Lisp.reallyParse("(app beta lambda)");
    assertTrue(term instanceof AppTerm);
    assertEquals(term, term.subst(new Substituter.TermSubst(() -> "lambda", new UnivTerm(Sort.SET0))));
  }

  @Test
  public void relatedSubst() {
    var term = Lisp.reallyParse("(app tony beta)", vars);
    assertTrue(term instanceof AppTerm);
    assertNotEquals(term, term.subst(new Substituter.TermSubst(vars.get("beta"), new UnivTerm(Sort.SET0))));
  }
}

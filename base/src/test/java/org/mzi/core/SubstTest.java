package org.mzi.core;

import org.junit.Test;
import org.mzi.core.subst.TermSubst;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.UnivTerm;
import org.mzi.test.Lisp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SubstTest {
  @Test
  public void emptySubst() {
    var term = Lisp.reallyParse("(app f a)");
    assertTrue(term instanceof AppTerm);
    assertEquals(term, term.subst(TermSubst.EMPTY));
  }

  @Test
  public void unrelatedSubst() {
    var term = Lisp.reallyParse("(app f a)");
    assertTrue(term instanceof AppTerm);
    assertEquals(term, term.subst(new TermSubst(() -> "a", new UnivTerm())));
  }
}

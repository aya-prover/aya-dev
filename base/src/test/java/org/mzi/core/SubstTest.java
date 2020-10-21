package org.mzi.core;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mzi.api.ref.Ref;
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
    assertEquals(term, term.subst(new TermSubst(() -> "lambda", new UnivTerm(UnivTerm.Sort.SET0))));
  }

  @Test
  public void relatedSubst() {
    @NotNull Map<String, @NotNull Ref> refs = new TreeMap<>();
    var term = Lisp.reallyParse("(app tony beta)", refs);
    assertTrue(term instanceof AppTerm);
    assertNotEquals(term, term.subst(new TermSubst(refs.get("beta"), new UnivTerm(UnivTerm.Sort.SET0))));
  }
}

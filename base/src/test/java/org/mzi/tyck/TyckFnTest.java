// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.glavo.kala.Unit;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.junit.jupiter.api.Test;
import org.mzi.api.error.SourcePos;
import org.mzi.concrete.Expr;
import org.mzi.concrete.Param;
import org.mzi.concrete.desugar.ExprDesugarer;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.LamTerm;
import org.mzi.core.term.PiTerm;
import org.mzi.core.term.RefTerm;
import org.mzi.generic.Arg;
import org.mzi.ref.LocalVar;
import org.mzi.test.Lisp;
import org.mzi.test.ThrowingReporter;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test if the tycker is functioning, say, working for simple cases.
 *
 * @author ice1000
 */
public class TyckFnTest {
  @Test
  public void idLamConnected() {
    var a = new LocalVar("a");
    // \A a.a
    idLamTestCase(new Expr.TelescopicLamExpr(SourcePos.NONE,
      Buffer.of(
        new Param(SourcePos.NONE, new LocalVar("_"), true),
        new Param(SourcePos.NONE, a, true)),
      new Expr.RefExpr(SourcePos.NONE, a)));
  }

  @Test
  public void idLamDisconnected() {
    var a = new LocalVar("a");
    // \A.\a.a
    idLamTestCase(new Expr.TelescopicLamExpr(SourcePos.NONE,
      Buffer.of(new Param(SourcePos.NONE, new LocalVar("_"), true)),
      new Expr.TelescopicLamExpr(SourcePos.NONE, Buffer.of(new Param(SourcePos.NONE, a, true)),
        new Expr.RefExpr(SourcePos.NONE, a))));
  }

  private void idLamTestCase(Expr lamAaa) {
    var piUAA = Lisp.parse("(Pi (A (U) ex) (Pi (a A ex) A))");
    var result = lamAaa.desugar()
      .accept(new ExprTycker(ThrowingReporter.INSTANCE), piUAA);
    assertNotNull(result);
    if (!(result.wellTyped() instanceof LamTerm lam && result.type() instanceof PiTerm dt)) {
      fail();
      return;
    }
    var lam_aa = AppTerm.make(lam, new Arg<>(new RefTerm(() -> "_"), true));
    assertEquals(lam.body(), lam_aa);
    var newVar = new RefTerm(new LocalVar("xyr"));
    assertEquals(newVar, AppTerm.make(lam_aa, new Arg<>(newVar, true)));
    assertTrue(dt.body() instanceof PiTerm pi
      && pi.body() instanceof RefTerm ref
      && ref.var() == dt.param().ref());
  }

  @Test
  public void uncurryLam() {
    var p = new LocalVar("p");
    var pRef = new Expr.RefExpr(SourcePos.NONE, p);
    var f = new LocalVar("f");
    // \A B C f p. f(p.1, p.2)
    var uncurry = new Expr.TelescopicLamExpr(SourcePos.NONE,
      Stream
        .concat(
          Stream.of("A", "B", "C").map(LocalVar::new),
          Stream.of(f, p)
        )
        .map(v -> new Param(SourcePos.NONE, v, true))
        .collect(Buffer.factory()),
      new Expr.AppExpr(SourcePos.NONE,
        new Expr.RefExpr(SourcePos.NONE, f),
        ImmutableSeq.of(
          new Arg<>(new Expr.ProjExpr(SourcePos.NONE, pRef, 1), true),
          new Arg<>(new Expr.ProjExpr(SourcePos.NONE, pRef, 2), true))));
    // Pi(A B C : U)(f : A -> B -> C)(p : A ** B) -> C
    var uncurryTy = Lisp.parse("""
      (Pi (A (U) ex)
       (Pi (B (U) ex)
        (Pi (C (U) ex)
         (Pi (f (Pi (a A ex)
                 (Pi (b B ex) C)) ex)
          (Pi (p (Sigma (a A ex null) B) ex) C)))))""");
    uncurry.desugar().accept(new ExprTycker(ThrowingReporter.INSTANCE), uncurryTy);
  }
}

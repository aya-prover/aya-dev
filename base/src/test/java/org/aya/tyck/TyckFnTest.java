// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.error.SourcePos;
import org.aya.concrete.Expr;
import org.aya.concrete.parse.MziProducer;
import org.aya.core.term.AppTerm;
import org.aya.core.term.LamTerm;
import org.aya.core.term.PiTerm;
import org.aya.core.term.RefTerm;
import org.aya.generic.Arg;
import org.aya.ref.LocalVar;
import org.aya.test.Lisp;
import org.aya.test.ThrowingReporter;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.Test;

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
    idLamTestCase(lamConnected(), new ExprTycker(ThrowingReporter.INSTANCE));
  }

  static @NotNull Expr lamConnected() {
    var a = new LocalVar("a");
    // \A a.a
    return MziProducer.buildLam(SourcePos.NONE,
      ImmutableSeq.of(
        new Expr.Param(SourcePos.NONE, new LocalVar("_"), true),
        new Expr.Param(SourcePos.NONE, a, true)).view(),
      new Expr.RefExpr(SourcePos.NONE, a));
  }

  @Test
  public void idLamDisconnected() {
    var a = new LocalVar("a");
    // \A.\a.a
    final var lam = new Expr.LamExpr(SourcePos.NONE,
      new Expr.Param(SourcePos.NONE, new LocalVar("_"), true),
      new Expr.LamExpr(SourcePos.NONE, new Expr.Param(SourcePos.NONE, a, true),
        new Expr.RefExpr(SourcePos.NONE, a)));
    idLamTestCase(lam, new ExprTycker(ThrowingReporter.INSTANCE));
  }

  @TestOnly
  static void idLamTestCase(Expr lamAaa, ExprTycker visitor) {
    var piUAA = Lisp.parse("(Pi (A (U) ex) (Pi (a A ex) A))");
    var result = lamAaa
      .accept(visitor, piUAA);
    assertNotNull(result);
    if (!(result.wellTyped() instanceof LamTerm lam && result.type() instanceof PiTerm dt)) {
      fail();
      return;
    }
    var lam_aa = AppTerm.make(lam, new Arg<>(new RefTerm(new LocalVar("_")), true));
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
    var uncurry = MziProducer.buildLam(SourcePos.NONE,
      Stream
        .concat(
          Stream.of("A", "B", "C").map(LocalVar::new),
          Stream.of(f, p)
        )
        .map(v -> new Expr.Param(SourcePos.NONE, v, true))
        .collect(ImmutableSeq.factory())
        .view(),
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
    uncurry.accept(new ExprTycker(ThrowingReporter.INSTANCE), uncurryTy);
  }
}

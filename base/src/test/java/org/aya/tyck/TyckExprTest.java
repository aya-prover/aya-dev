// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.concrete.Expr;
import org.aya.concrete.parse.AyaProducer;
import org.aya.core.term.CallTerm;
import org.aya.core.term.FormTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.RefTerm;
import org.aya.test.Lisp;
import org.aya.test.ThrowingReporter;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Either;
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
public class TyckExprTest {
  @Test
  public void idLamConnected() {
    idLamTestCase(lamConnected(), tycker());
  }

  @TestOnly public static @NotNull Expr lamConnected() {
    var a = new LocalVar("a");
    // \A a.a
    return AyaProducer.buildLam(SourcePos.NONE,
      ImmutableSeq.of(
        new Expr.Param(SourcePos.NONE, new LocalVar("_"), true),
        new Expr.Param(SourcePos.NONE, a, true)).view(),
      new Expr.RefExpr(SourcePos.NONE, a, "a"));
  }

  @Test
  public void idLamDisconnected() {
    var a = new LocalVar("a");
    // \A.\a.a
    final var lam = new Expr.LamExpr(SourcePos.NONE,
      new Expr.Param(SourcePos.NONE, new LocalVar("_"), true),
      new Expr.LamExpr(SourcePos.NONE, new Expr.Param(SourcePos.NONE, a, true),
        new Expr.RefExpr(SourcePos.NONE, a, "a")));
    idLamTestCase(lam, tycker());
  }

  @TestOnly
  static void idLamTestCase(Expr lamAaa, ExprTycker visitor) {
    var piUAA = Lisp.parse("(Pi (A (U) ex) (Pi (a A ex) A))");
    var result = lamAaa
      .accept(visitor, piUAA);
    assertNotNull(result);
    if (!(result.wellTyped() instanceof IntroTerm.Lambda lam && result.type() instanceof FormTerm.Pi dt)) {
      fail();
      return;
    }
    var lam_aa = CallTerm.make(lam, new Arg<>(new RefTerm(new LocalVar("_")), true));
    assertEquals(lam.body().toDoc(), lam_aa.toDoc());
    var newVar = new RefTerm(new LocalVar("xyr"));
    assertEquals(newVar, CallTerm.make(lam_aa, new Arg<>(newVar, true)));
    assertTrue(dt.body() instanceof FormTerm.Pi pi
      && pi.body() instanceof RefTerm ref
      && ref.var() == dt.param().ref());
  }

  @Test
  public void uncurryLam() {
    var p = new LocalVar("p");
    var pRef = new Expr.RefExpr(SourcePos.NONE, p, "p");
    var f = new LocalVar("f");
    // \A B C f p. f(p.1, p.2)
    var uncurry = AyaProducer.buildLam(SourcePos.NONE,
      Stream
        .concat(
          Stream.of("A", "B", "C").map(LocalVar::new),
          Stream.of(f, p)
        )
        .map(v -> new Expr.Param(SourcePos.NONE, v, true))
        .collect(ImmutableSeq.factory())
        .view(),
      new Expr.AppExpr(SourcePos.NONE,
        new Expr.RefExpr(SourcePos.NONE, f, "p"),
        ImmutableSeq.of(
          new Arg<>(new Expr.ProjExpr(SourcePos.NONE, pRef, Either.left(1)), true),
          new Arg<>(new Expr.ProjExpr(SourcePos.NONE, pRef, Either.left(2)), true))));
    // Pi(A B C : U)(f : A -> B -> C)(p : A ** B) -> C
    var uncurryTy = Lisp.parse("""
      (Pi (A (U) ex)
       (Pi (B (U) ex)
        (Pi (C (U) ex)
         (Pi (f (Pi (a A ex)
                 (Pi (b B ex) C)) ex)
          (Pi (p (Sigma (a A ex null) B) ex) C)))))""");
    uncurry.accept(tycker(), uncurryTy);
  }

  private static @NotNull ExprTycker tycker() {
    return new ExprTycker(ThrowingReporter.INSTANCE, null);
  }
}

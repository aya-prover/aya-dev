// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cubical;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Tuple3;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.cli.parse.AyaProducer;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.PrimDef;
import org.aya.core.repr.AyaShape;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Subst;
import org.aya.distill.BaseDistiller;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.LocalVar;
import org.aya.resolve.context.EmptyContext;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CofTest {
  private @NotNull Tuple3<LocalVar, Restr<Term>, Term> tyck(
    @Language("TEXT") String cof,
    @Language("TEXT") String to,
    @NotNull String i,
    @NotNull String... vars
  ) {
    var producer = new AyaProducer(Either.left(SourceFile.NONE), ThrowingReporter.INSTANCE);
    var cofE = producer.visitRestr(AyaParserImpl.parser(cof).restr());
    var toE = producer.visitExpr(AyaParserImpl.parser(to).expr());
    var localVars = Arrays.stream(vars).map(LocalVar::new).collect(ImmutableSeq.factory());
    var ctx = new EmptyContext(ThrowingReporter.INSTANCE, Path.of("TestSource"))
      .derive("TestModule");
    localVars.forEach(v -> ctx.addGlobalSimple(Stmt.Accessibility.Public, v, SourcePos.NONE));
    return tyck(localVars, cofE.fmap(e -> e.resolve(ctx)), toE.resolve(ctx))
      .cons(localVars.first(v -> v.name().equals(i)));
  }

  private @NotNull Tuple2<Restr<Term>, Term> tyck(
    @NotNull ImmutableSeq<LocalVar> localVars,
    @NotNull Restr<Expr> cof, @NotNull Expr subst
  ) {
    var primFactory = new PrimDef.Factory();
    var shapeFactory = new AyaShape.Factory();
    var exprTycker = new ExprTycker(primFactory, shapeFactory, ThrowingReporter.INSTANCE, null);
    localVars.forEach(v -> exprTycker.localCtx.put(v, FormTerm.Interval.INSTANCE));
    var restr = exprTycker.restr(cof);
    var term = exprTycker.inherit(subst, FormTerm.Interval.INSTANCE).wellTyped();
    return Tuple.of(restr, term);
  }

  public @NotNull Restr<Term> substCof(@Language("TEXT") String cof, String i, @Language("TEXT") String to, String... vars) {
    var tup = tyck(cof, to, i, vars);
    var subst = new Subst(tup._1, tup._3);
    return subst.restr(new TyckState(new PrimDef.Factory()), tup._2);
  }

  private static void assertDoc(@Language("TEXT") String expected, Restr<Term> actual) {
    assertEquals(expected, BaseDistiller.restr(DistillerOptions.debug(), actual).commonRender());
  }

  @Test public void simpleSubst() {
    var cof = substCof("i 0 ∨ j 1", "i", "k", "i", "j", "k");
    assertDoc("k 0 ∨ j 1", cof);
  }

  @Test public void substWithMax() {
    // (i 1 \/ j 1) [i |-> k \/ l]
    assertDoc("k 1 ∨ l 1 ∨ j 1", substCof(
      "i 1 ∨ j 1", "i", "k ∨ l", "i", "j", "k", "l"));
    // (i 0 \/ j 1) [i |-> k \/ l]
    assertDoc("j 1 ∧ l 0 ∧ k 0", substCof(
      "i 0 ∧ j 1", "i", "k ∨ l", "i", "j", "k", "l"));
    // Counter-intuitive for people unfamiliar with lattice theory:
    // (i 1 \/ j 1) [i |-> k \/ l]
    assertDoc("(k 1 ∧ j 1) ∨ (l 1 ∧ j 1)", substCof(
      "i 1 ∧ j 1", "i", "k ∨ l", "i", "j", "k", "l"));
    // (i 0 \/ j 1) [i |-> k \/ l]
    assertDoc("(l 0 ∧ k 0) ∨ j 1", substCof(
      "i 0 ∨ j 1", "i", "k ∨ l", "i", "j", "k", "l"));
  }

  @Test public void substWithMin() {
    // (i 1 \/ j 1) [i |-> k /\ l]
    assertDoc("(l 1 ∧ k 1) ∨ j 1", substCof(
      "i 1 ∨ j 1", "i", "k ∧ l", "i", "j", "k", "l"));
    // (i 0 \/ j 1) [i |-> k /\ l]
    assertDoc("(k 0 ∧ j 1) ∨ (l 0 ∧ j 1)", substCof(
      "i 0 ∧ j 1", "i", "k ∧ l", "i", "j", "k", "l"));
    // (i 1 \/ j 1) [i |-> k /\ l]
    assertDoc("j 1 ∧ l 1 ∧ k 1", substCof(
      "i 1 ∧ j 1", "i", "k ∧ l", "i", "j", "k", "l"));
    // (i 0 \/ j 1) [i |-> k /\ l]
    assertDoc("k 0 ∨ l 0 ∨ j 1", substCof(
      "i 0 ∨ j 1", "i", "k ∧ l", "i", "j", "k", "l"));
  }
}

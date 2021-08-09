// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.ParseTest;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.module.EmptyModuleLoader;
import org.aya.concrete.resolve.visitor.StmtShallowResolver;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.RefTerm;
import org.aya.test.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TyckDeclTest {
  private FnDef successTyckFn(@NotNull @NonNls @Language("TEXT") String code) {
    var decl = ParseTest.parseDecl(code)._1;
    decl.ctx = new EmptyContext(ThrowingReporter.INSTANCE).derive();
    var opSet = new BinOpSet(ThrowingReporter.INSTANCE);
    decl.resolve(opSet);
    opSet.sort();
    decl.desugar(ThrowingReporter.INSTANCE, opSet);
    var def = decl.tyck(ThrowingReporter.INSTANCE, null, PrimDef.PrimFactory.create());
    assertNotNull(def);
    assertTrue(def instanceof FnDef);
    return ((FnDef) def);
  }

  @Test public void ctorPatScoping() {
    var defs = successTyckDecls("""
      data Nat : Set | zero | suc Nat
      def xyr (zero : Nat) : Nat
        | zero => zero
        | suc n => zero""");
    var nat = (DataDef) defs.get(0);
    var xyr = (FnDef) defs.get(1);
      var ctors = nat.body;
    assertEquals(2, ctors.size());
    var clauses = xyr.body.getRightValue();
    var zeroToZero = clauses.get(0);
    var zeroCtor = ctors.get(0);
    assertEquals(0, zeroCtor.selfTele.size());
    var zeroParam = xyr.telescope().get(0);
    assertEquals(((RefTerm) zeroToZero.body()).var(), zeroParam.ref());
    assertEquals(zeroCtor.ref(), ((Pat.Ctor) zeroToZero.patterns().get(0)).ref());
  }

  public static @NotNull ImmutableSeq<Stmt> successDesugarDecls(@Language("TEXT") @NonNls @NotNull String text) {
    var decls = ParseTest.INSTANCE
      .visitProgram(AyaParsing.parser(text).program());
    var ssr = new StmtShallowResolver(new EmptyModuleLoader());
    var ctx = new EmptyContext(ThrowingReporter.INSTANCE).derive();
    decls.forEach(d -> d.accept(ssr, ctx));
    var opSet = new BinOpSet(ThrowingReporter.INSTANCE);
    decls.forEach(s -> s.resolve(opSet));
    opSet.sort();
    decls.forEach(stmt -> stmt.desugar(ThrowingReporter.INSTANCE, opSet));
    return decls;
  }

  public static @NotNull ImmutableSeq<Def> successTyckDecls(@Language("TEXT") @NonNls @NotNull String text) {
    var primFactory = PrimDef.PrimFactory.create();
    return successDesugarDecls(text)
      .map(i -> i instanceof Decl s ? s.tyck(ThrowingReporter.INSTANCE, null, primFactory) : null)
      .filter(Objects::nonNull);
  }
}

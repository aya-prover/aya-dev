// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.ref.Var;
import org.aya.concrete.Decl;
import org.aya.concrete.ParseTest;
import org.aya.concrete.Signatured;
import org.aya.concrete.Stmt;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.module.EmptyModuleLoader;
import org.aya.concrete.resolve.visitor.StmtShallowResolver;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.RefTerm;
import org.aya.test.Lisp;
import org.aya.test.ThrowingReporter;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class TyckDeclTest {
  @Test
  public void testIdFunc() {
    idFuncTestCase("\\def id {A : \\Set} (a : A) : A => a");
    idFuncTestCase("\\def id {A : \\Set} (a : A) => a");
  }

  public void idFuncTestCase(@NotNull @NonNls @Language("TEXT") String code) {
    var fnDef = successTyckFn(code);
    var vars = new HashMap<String, @NotNull Var>();
    vars.put(fnDef.ref().name(), fnDef.ref());
    var expected = Lisp.parseDef("id",
      "(A (U) im (a (A) ex null))", "A", "a", vars);
    assertEquals(expected, fnDef);
  }

  private FnDef successTyckFn(@NotNull @NonNls @Language("TEXT") String code) {
    var decl = ParseTest.parseDecl(code)._1;
    decl.ctx = new EmptyContext(ThrowingReporter.INSTANCE).derive();
    decl.resolve();
    var def = decl.tyck(ThrowingReporter.INSTANCE, null);
    assertNotNull(def);
    assertTrue(def instanceof FnDef);
    return ((FnDef) def);
  }

  @Test
  public void ctorPatScoping(){
    var defs = successTyckDecls("""
      \\data Nat : \\Set | zero | suc Nat
      \\def xyr (zero : Nat) : Nat
        | zero => zero
        | suc n => zero""");
    var nat = (DataDef) defs.get(0);
    var xyr = (FnDef) defs.get(1);
    assertEquals(2, nat.ctors().size());
    var clauses = xyr.body().getRightValue();
    var zeroToZero = ((Pat.Clause.Match) clauses.get(0));
    var zeroCtor = nat.ctors().get(0);
    assertEquals(0, zeroCtor.conTelescope().size());
    var zeroParam = xyr.telescope().get(0);
    assertEquals(zeroToZero.expr(), new RefTerm(zeroParam.ref()));
    assertEquals(zeroCtor.ref(), ((Pat.Ctor) zeroToZero.patterns().get(0)).ref());
  }

  private @NotNull ImmutableSeq<Def> successTyckDecls(@Language("TEXT") @NonNls @NotNull String text) {
    var decls = ParseTest.INSTANCE
      .visitProgram(AyaParsing.parser(text).program())
      .map(i -> (Decl) i);
    var ssr = new StmtShallowResolver(new EmptyModuleLoader());
    var ctx = new EmptyContext(ThrowingReporter.INSTANCE).derive();
    decls.forEach(d -> d.accept(ssr, ctx));
    decls.forEach(Stmt::resolve);
    return decls
      .map(i -> (Signatured) i)
      .map(i -> i.tyck(ThrowingReporter.INSTANCE, null));
  }
}

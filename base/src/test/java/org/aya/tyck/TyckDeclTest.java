// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.ParseTest;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.module.EmptyModuleLoader;
import org.aya.concrete.resolve.visitor.StmtShallowResolver;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.test.ThrowingReporter;
import org.aya.tyck.trace.Trace;
import org.aya.util.error.SourceFile;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TyckDeclTest {
  public static Def tyck(@NotNull Decl decl, Trace.@Nullable Builder builder) {
    var tycker = new StmtTycker(ThrowingReporter.INSTANCE, builder, new TyckOptions(true));
    return tycker.tyck(decl, tycker.newTycker());
  }

  private FnDef successTyckFn(@NotNull @NonNls @Language("TEXT") String code) {
    var decl = ParseTest.parseDecl(code)._1;
    decl.ctx = new EmptyContext(ThrowingReporter.INSTANCE).derive("decl");
    resolve(ImmutableSeq.of(decl));
    var def = tyck(decl, null);
    assertNotNull(def);
    assertTrue(def instanceof FnDef);
    return ((FnDef) def);
  }

  public static @NotNull ImmutableSeq<Stmt> successDesugarDecls(@Language("TEXT") @NonNls @NotNull String text) {
    var decls = AyaParsing.program(ThrowingReporter.INSTANCE, new SourceFile(Path.of("114514"), text));
    var ssr = new StmtShallowResolver(new EmptyModuleLoader(), null);
    var ctx = new EmptyContext(ThrowingReporter.INSTANCE).derive("decl");
    decls.forEach(d -> d.accept(ssr, ctx));
    resolve(decls);
    return decls;
  }

  private static void resolve(@NotNull ImmutableSeq<Stmt> decls) {
    Stmt.resolve(decls, new ResolveInfo(new AyaBinOpSet(ThrowingReporter.INSTANCE)));
  }

  public static @NotNull ImmutableSeq<Def> successTyckDecls(@Language("TEXT") @NonNls @NotNull String text) {
    return successDesugarDecls(text).view()
      .map(i -> i instanceof Decl s ? tyck(s, null) : null)
      .filter(Objects::nonNull).toImmutableSeq();
  }
}

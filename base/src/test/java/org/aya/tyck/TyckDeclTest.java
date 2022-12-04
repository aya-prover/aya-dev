// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.concrete.ParseTest;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.GenericDef;
import org.aya.core.def.PrimDef;
import org.aya.core.repr.AyaShape;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.module.EmptyModuleLoader;
import org.aya.tyck.trace.Trace;
import org.aya.util.reporter.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TyckDeclTest {
  public static GenericDef tyck(@NotNull PrimDef.Factory factory, @NotNull TeleDecl<?> decl, Trace.@Nullable Builder builder, @NotNull AyaShape.Factory shapes) {
    var tycker = new StmtTycker(ThrowingReporter.INSTANCE, builder);
    var def = tycker.tyck(decl, tycker.newTycker(factory, shapes));
    shapes.bonjour(def);
    return def;
  }

  public static @NotNull Tuple2<PrimDef.Factory, ImmutableSeq<Stmt>> successDesugarDecls(@Language("Aya") @NonNls @NotNull String text) {
    var decls = ParseTest.parseStmt(text);
    var ctx = new EmptyContext(ThrowingReporter.INSTANCE, Path.of("TestSource")).derive("decl");
    var factory = resolve(decls, ctx);
    return Tuple.of(factory, decls);
  }

  public static @NotNull PrimDef.Factory resolve(@NotNull ImmutableSeq<Stmt> decls, @NotNull ModuleContext module) {
    var primFactory = new PrimDef.Factory();
    Stmt.resolve(decls, new ResolveInfo(primFactory, module, decls), EmptyModuleLoader.INSTANCE);
    assertNotNull(module.underlyingFile());
    return primFactory;
  }

  public static @NotNull Tuple2<PrimDef.Factory, ImmutableSeq<GenericDef>> successTyckDecls(@Language("Aya") @NonNls @NotNull String text) {
    var res = successDesugarDecls(text);
    var shapes = new AyaShape.Factory();
    return Tuple.of(res._1, res._2.view()
      .map(i -> i instanceof TeleDecl<?> s ? tyck(res._1, s, null, shapes) : null)
      .filter(Objects::nonNull).toImmutableSeq());
  }
}

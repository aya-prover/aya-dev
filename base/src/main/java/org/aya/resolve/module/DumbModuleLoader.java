// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import org.aya.primitive.PrimFactory;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.ref.QPath;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class DumbModuleLoader implements ModuleLoader {
  public static final @NonNls @NotNull String DUMB_MODULE_STRING = "baka";
  public static final @NotNull QPath DUMB_MODULE_NAME = new QPath(ModulePath.of(DUMB_MODULE_STRING), 1);

  public final @NotNull PrimFactory primFactory = new PrimFactory();
  public final @NotNull Context baseContext;
  public DumbModuleLoader(@NotNull Context baseContext) {
    this.baseContext = baseContext;
  }

  public @NotNull ResolveInfo resolve(@NotNull ImmutableSeq<Stmt> stmts) {
    try {
      return resolveModule(primFactory, baseContext.derive(DUMB_MODULE_NAME.module()), stmts, this);
    } catch (Context.ResolvingInterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override public @NotNull ResolveInfo load(@NotNull ModulePath path, @NotNull ModuleLoader recurseLoader) {
    throw new UnsupportedOperationException();
  }

  @Override public boolean existsFileLevelModule(@NotNull ModulePath path) { return false; }
  @Override public @NotNull Reporter reporter() { return baseContext.reporter(); }
}

// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import org.aya.primitive.PrimFactory;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DumbModuleLoader implements ModuleLoader {
  public static final @NotNull String DUMB_MODULE_NAME = "baka";

  public final @NotNull PrimFactory primFactory = new PrimFactory();
  public final @NotNull Context baseContext;
  public DumbModuleLoader(@NotNull Context baseContext) {
    this.baseContext = baseContext;
  }

  public @NotNull ResolveInfo resolve(@NotNull ImmutableSeq<Stmt> stmts) {
    return resolveModule(primFactory, baseContext.derive(DUMB_MODULE_NAME), stmts, this);
  }

  @Override public @Nullable ResolveInfo load(@NotNull ModulePath path, @NotNull ModuleLoader recurseLoader) {
    throw new UnsupportedOperationException();
  }
  @Override public boolean existsFileLevelModule(@NotNull ModulePath path) { return false; }
  @Override public @NotNull Reporter reporter() { return baseContext.reporter(); }
}

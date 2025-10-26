// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Result;
import org.aya.primitive.PrimFactory;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.LoadErrorKind;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.ref.QPath;
import org.aya.util.reporter.CountingReporter;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class DumbModuleLoader implements ModuleLoader {
  public static final @NonNls @NotNull String DUMB_MODULE_STRING = "baka";
  public static final @NotNull QPath DUMB_MODULE_NAME = new QPath(ModulePath.of(DUMB_MODULE_STRING), 1);

  public final @NotNull PrimFactory primFactory = new PrimFactory();
  private final @NotNull CountingReporter reporter;
  public final @NotNull Context baseContext;

  public DumbModuleLoader(@NotNull Reporter reporter, @NotNull Context baseContext) {
    this.reporter = CountingReporter.delegate(reporter);
    this.baseContext = baseContext;
  }

  public @NotNull ResolveInfo resolve(@NotNull ImmutableSeq<Stmt> stmts) {
    return Objects.requireNonNull(
      resolveModule(primFactory, baseContext.derive(DUMB_MODULE_NAME.module()), stmts, this),
      "Resolve");
  }

  @Override public @NotNull Result<ResolveInfo, LoadErrorKind> load(@NotNull ModulePath path, @NotNull ModuleLoader recurseLoader) {
    throw new UnsupportedOperationException();
  }

  @Override public boolean existsFileLevelModule(@NotNull ModulePath path) { return false; }
  @Override public @NotNull CountingReporter reporter() { return reporter; }
}

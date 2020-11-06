// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.context;

import asia.kala.Tuple2;
import asia.kala.collection.Set;
import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.concrete.Stmt;

import java.util.function.BiConsumer;

/**
 * @author re-xyr
 */
public final class ModuleContext implements Context {
  @NotNull Context ctx;
  @NotNull Stmt.CmdStmt.UseHide useHide;

  public ModuleContext(@NotNull Context ctx, @NotNull Stmt.CmdStmt.UseHide useHide) {
    useHide.list().forEach(name -> {
      if (!ctx.containsLocal(name)) throw new IllegalStateException("Module does not contain name `" + name + "`"); // TODO[xyr]: report instead of throw
    });
    this.ctx = ctx;
    this.useHide = useHide;
  }

  @Override
  public @Nullable Var getLocal(@NotNull String name, Stmt.@NotNull Accessibility accessibility) {
    var pre = ctx.getLocal(name, accessibility);
    return useHide.uses(name) ? pre : null;
  }

  @Override
  public boolean containsLocal(@NotNull String name) {
    var pre = ctx.containsLocal(name);
    if (pre) return useHide.uses(name);
    else return false;
  }

  @Override
  public void forEachLocal(@NotNull BiConsumer<@NotNull String, @NotNull Tuple2<@NotNull Var, Stmt.@NotNull Accessibility>> f) {
    ctx.forEachLocal((name, data) -> {
      if (useHide.uses(name)) f.accept(name, data);
    });
  }

  @Override
  public void unsafePutLocal(@NotNull String name, @NotNull Var ref, Stmt.@NotNull Accessibility accessibility) {
    throw new IllegalStateException("Unable to extend a ModuleContext");
  }

  @Override
  public boolean containsSubContextLocal(@NotNull String name) {
    return false;
  }

  @Override
  public @Nullable Context getSubContextLocal(@NotNull String name, Stmt.@NotNull Accessibility accessibility) {
    return null;
  }

  @Override
  public void unsafePutSubContextLocal(@NotNull String name, @NotNull Context ctx, Stmt.@NotNull Accessibility accessibility) {
    throw new IllegalStateException("Unable to extend a ModuleContext");
  }

  @Override
  public @Nullable Context getSuperContext() {
    return null;
  }

  @Override
  public void setSuperContext(@NotNull Context ctx) {
    throw new IllegalStateException("Unable to extend a ModuleContext");
  }
}

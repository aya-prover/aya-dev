// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.module;

import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.concrete.Stmt;
import org.mzi.concrete.resolve.context.Context;
import org.mzi.concrete.resolve.context.ModuleContext;
import org.mzi.concrete.resolve.context.SimpleContext;

/**
 * @author re-xyr
 */
public interface ModuleLoader {
  @Nullable Context unsafeLoad(@NotNull ImmutableSeq<@NotNull String> path);

  default @Nullable Context load(@NotNull ImmutableSeq<@NotNull String> path,
                                 @NotNull Stmt.CmdStmt.UseHide useHide) {
    var ctx = unsafeLoad(path);
    if (ctx == null) return null;
    return new ModuleContext(ctx, useHide);
  }

  default @Nullable Context load(@NotNull ImmutableSeq<@NotNull String> path) {
    return load(path, new Stmt.CmdStmt.UseHide(ImmutableSeq.empty(), Stmt.CmdStmt.UseHide.Strategy.Hiding));
  }

  default boolean loadIntoContext(@NotNull Context context,
                                  @NotNull ImmutableSeq<@NotNull String> path,
                                  @NotNull Stmt.CmdStmt.UseHide useHide) {
    var subCtx = load(path, useHide);
    if (subCtx == null) return false;
    var ctx = context;
    for (var nm : path.dropLast(1)) {
      if (ctx.containsModuleLocal(nm)) ctx = ctx.getModuleLocal(nm);
      else {
        var nextCtx = new SimpleContext();
        ctx.putModuleLocal(nm, nextCtx);
        ctx = nextCtx;
      }
      assert ctx != null;
    }
    ctx.putModuleLocal(path.last(), subCtx);
    return true;
  }
}

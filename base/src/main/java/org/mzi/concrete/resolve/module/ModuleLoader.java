// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.module;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.concrete.Stmt;
import org.mzi.concrete.resolve.context.Context;
import org.mzi.concrete.resolve.context.SimpleContext;
import org.mzi.concrete.resolve.context.ModuleContext;

/**
 * @author re-xyr
 */
public interface ModuleLoader {
  @Nullable Context unsafeLoad(@NotNull ImmutableSeq<@NotNull String> path);

  default @Nullable Context load(@NotNull ImmutableSeq<@NotNull String> path,
                                 @NotNull ImmutableSeq<@NotNull String> useHideList,
                                 @NotNull Stmt.CmdStmt.Strategy strategy) {
    var ctx = unsafeLoad(path);
    if (ctx == null) return null;
    return new ModuleContext(ctx, useHideList, strategy);
  }

  default @Nullable Context load(@NotNull ImmutableSeq<@NotNull String> path) {
    return load(path, ImmutableSeq.of(), Stmt.CmdStmt.Strategy.Hiding);
  }

  default boolean loadIntoContext(@NotNull Context context,
                                  @NotNull ImmutableSeq<@NotNull String> path,
                                  @NotNull ImmutableSeq<@NotNull String> useHideList,
                                  @NotNull Stmt.CmdStmt.Strategy strategy,
                                  @NotNull Stmt.Accessibility accessibility) {
    var subCtx = load(path, useHideList, strategy);
    if (subCtx == null) return false;
    var ctx = context;
    for (var nm : path.dropLast(1)) {
      if (ctx.containsSubContextLocal(nm)) ctx = ctx.getSubContextLocal(nm);
      else {
        var nextCtx = new SimpleContext();
        ctx.putSubContextLocal(nm, nextCtx, Stmt.Accessibility.Public);
        ctx = nextCtx;
      }
      assert ctx != null;
    }
    ctx.putSubContextLocal(path.last(), subCtx, accessibility);
    return true;
  }
}

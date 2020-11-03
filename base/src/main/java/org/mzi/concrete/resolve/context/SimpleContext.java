// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.context;

import asia.kala.Tuple2;
import asia.kala.collection.mutable.MutableHashMap;
import asia.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.concrete.Stmt;

/**
 * @author re-xyr
 */
public final class SimpleContext implements Context {
  private final MutableMap<String, Tuple2<Var, Stmt.Accessibility>> variables = new MutableHashMap<>();
  private final MutableMap<String, Tuple2<Context, Stmt.Accessibility>> subContexts = new MutableHashMap<>();
  private Context superContext;

  @Override public @Nullable Var getLocal(@NotNull String name, Stmt.@NotNull Accessibility accessibility) {
    var variable = variables.get(name);
    if (variable == null || variable._2.ordinal() < accessibility.ordinal()) return null;
    return variable._1;
  }

  @Override public boolean containsLocal(@NotNull String name) {
    return variables.containsKey(name);
  }

  @Override public void unsafePutLocal(@NotNull String name, @NotNull Var ref, Stmt.@NotNull Accessibility accessibility) {
    variables.put(name, Tuple2.of(ref, accessibility));
  }

  @Override public boolean containsSubContextLocal(@NotNull String name) {
    return subContexts.containsKey(name);
  }

  @Override public @Nullable Context getSubContextLocal(@NotNull String name, Stmt.@NotNull Accessibility accessibility) {
    var ctx = subContexts.get(name);
    if (ctx == null || ctx._2.ordinal() < accessibility.ordinal()) return null;
    return ctx._1;
  }

  @Override public void unsafePutSubContextLocal(@NotNull String name, @NotNull Context ctx, Stmt.@NotNull Accessibility accessibility) {
    subContexts.put(name, Tuple2.of(ctx, accessibility));
  }

  @Override public @Nullable Context getSuperContext() {
    return superContext;
  }

  @Override public void setSuperContext(@NotNull Context ctx) {
    superContext = ctx;
  }
}

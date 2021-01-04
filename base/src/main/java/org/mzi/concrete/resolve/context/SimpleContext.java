// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.context;

import org.glavo.kala.Tuple2;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.concrete.Stmt;

import java.util.function.BiConsumer;

/**
 * @author re-xyr
 */
public final class SimpleContext implements Context {
  private final MutableMap<String, Tuple2<Var, Stmt.Accessibility>> variables = new MutableHashMap<>();
  private final MutableMap<String, Context> modules = new MutableHashMap<>();
  private Context superContext;

  @Override public @Nullable Tuple2<Var, Stmt.Accessibility> unsafeGetLocal(@NotNull String name) {
    return variables.get(name);
  }

  @Override public @Nullable Stmt.Accessibility unsafeContainsLocal(@NotNull String name) {
    return Option.of(variables.get(name))
      .map(i -> i._2)
      .getOrNull();
  }

  @Override
  public void unsafeForEachLocal(@NotNull BiConsumer<@NotNull String, @NotNull Tuple2<@NotNull Var, Stmt.@NotNull Accessibility>> f) {
    variables.forEach(f);
  }

  @Override public void unsafePutLocal(@NotNull String name, @NotNull Var ref, Stmt.@NotNull Accessibility accessibility) {
    variables.put(name, Tuple2.of(ref, accessibility));
  }

  @Override public boolean containsModuleLocal(@NotNull String name) {
    return modules.containsKey(name);
  }

  @Override public @Nullable Context getModuleLocal(@NotNull String name) {
    return modules.get(name);
  }

  @Override public void unsafePutModuleLocal(@NotNull String name, @NotNull Context ctx) {
    modules.put(name, ctx);
  }

  @Override public @Nullable Context getOuterContext() {
    return superContext;
  }

  @Override public void setOuterContext(@NotNull Context ctx) {
    superContext = ctx;
  }
}

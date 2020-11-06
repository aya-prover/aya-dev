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

import java.util.function.BiConsumer;

/**
 * @author re-xyr
 */
public final class SimpleContext implements Context {
  private final MutableMap<String, Tuple2<Var, Stmt.Accessibility>> variables = new MutableHashMap<>();
  private final MutableMap<String, Context> modules = new MutableHashMap<>();
  private Context superContext;

  @Override public @Nullable Var getLocal(@NotNull String name, Stmt.@NotNull Accessibility accessibility) {
    var variable = variables.get(name);
    if (variable == null || variable._2.ordinal() < accessibility.ordinal()) return null;
    return variable._1;
  }

  @Override public boolean containsLocal(@NotNull String name) {
    return variables.containsKey(name);
  }

  @Override
  public void forEachLocal(@NotNull BiConsumer<@NotNull String, @NotNull Tuple2<@NotNull Var, Stmt.@NotNull Accessibility>> f) {
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

  @Override public @Nullable Context getGlobal() {
    return superContext;
  }

  @Override public void setGlobal(@NotNull Context ctx) {
    superContext = ctx;
  }
}

// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.context;

import org.glavo.kala.Tuple2;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.concrete.Stmt;

import java.util.function.BiConsumer;

/**
 * @author re-xyr
 */
public interface Context {
  @Nullable Tuple2<Var, Stmt.Accessibility> unsafeGetLocal(@NotNull String name);

  default @Nullable Var getLocal(@NotNull String name, Stmt.@NotNull Accessibility accessibility) {
    var variable = unsafeGetLocal(name);
    if (variable == null || variable._2.lessThan(accessibility)) return null;
    return variable._1;
  }

  default @Nullable Var getLocal(@NotNull String name) {
    return getLocal(name, Stmt.Accessibility.Private);
  }

  default @Nullable Var getLocal(@NotNull Seq<@NotNull String> path) {
    assert path.size() > 0; // should not happen
    return Option.of(getModuleLocal(path.view().dropLast(1)))
      .flatMap(ctx -> Option.of(ctx.getLocal(path.last(), path.size() == 1 ? Stmt.Accessibility.Private : Stmt.Accessibility.Public)))
      .getOrNull();
  }

  @Nullable Stmt.Accessibility unsafeContainsLocal(@NotNull String name);

  default boolean containsLocal(@NotNull String name, @NotNull Stmt.Accessibility accessibility) {
    var acc = unsafeContainsLocal(name);
    return acc != null && !acc.lessThan(accessibility);
  }

  default boolean containsLocal(@NotNull String name) {
    return containsLocal(name, Stmt.Accessibility.Private);
  }

  void unsafeForEachLocal(@NotNull BiConsumer<@NotNull String, @NotNull Tuple2<@NotNull Var, Stmt.@NotNull Accessibility>> f);

  default void forEachLocal(@NotNull BiConsumer<@NotNull String, @NotNull Var> f, Stmt.@NotNull Accessibility accessibility) {
    unsafeForEachLocal((s, v) -> {
      if (v._2.ordinal() >= accessibility.ordinal()) f.accept(s, v._1);
    });
  }

  default @Nullable Var get(@NotNull String name, @NotNull Stmt.Accessibility accessibility) {
    return Option.of(getLocal(name, accessibility))
      .getOrElse(() ->
        Option.of(getOuterContext())
          .map(sup -> sup.get(name, accessibility))
          .getOrNull());
  }

  default @Nullable Var get(@NotNull String name) {
    return get(name, Stmt.Accessibility.Private);
  }

  default @Nullable Var get(@NotNull Seq<@NotNull String> path) {
    return Option.of(getLocal(path))
      .getOrElse(() ->
        Option.of(getOuterContext())
          .map(sup -> sup.get(path))
          .getOrNull());
  }

  void unsafePutLocal(@NotNull String name, @NotNull Var ref, Stmt.@NotNull Accessibility accessibility);

  default void putLocal(@NotNull String name, @NotNull Var ref, Stmt.@NotNull Accessibility accessibility) {
    // TODO[xyr]: should report instead of throw
    if (containsLocal(name)) throw new IllegalStateException("Trying to add duplicate ref `" + name + "` to a context");
    unsafePutLocal(name, ref, accessibility);
  }

  boolean containsModuleLocal(@NotNull String name);

  @Nullable Context getModuleLocal(@NotNull String name);

  default @Nullable Context getModuleLocal(@NotNull Seq<@NotNull String> path) {
    if (path.size() == 0) return this;
    return Option.of(getModuleLocal(path.first()))
      .flatMap(ctx -> Option.of(ctx.getModuleLocal(path.view().drop(1))))
      .getOrNull();
  }

  default @Nullable Context getModule(@NotNull String name) {
    return Option.of(getModuleLocal(name))
      .getOrElse(() ->
        Option.of(getOuterContext())
          .map(sup -> sup.getModule(name))
          .getOrNull());
  }

  default @Nullable Context getModule(@NotNull Seq<@NotNull String> path) {
    return Option.of(getModule(path.first()))
      .flatMap(ctx -> Option.of(ctx.getModuleLocal(path.view().drop(1))))
      .getOrNull();
  }

  void unsafePutModuleLocal(@NotNull String name, @NotNull Context ctx);

  default void putModuleLocal(@NotNull String name, @NotNull Context ctx) {
    // TODO[xyr]: should report instead of throw
    if (containsModuleLocal(name)) throw new IllegalStateException("Trying to add duplicate sub context `" + name + "` to a context");
    unsafePutModuleLocal(name, ctx);
  }

  @Nullable Context getOuterContext();

  void setOuterContext(@NotNull Context ctx);
}

// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.context;

import asia.kala.Tuple2;
import asia.kala.collection.Seq;
import asia.kala.control.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.concrete.Stmt;

import java.util.function.BiConsumer;

/**
 * @author re-xyr
 * @implNote subContext and superContext are NOT dual concepts. subContexts are local or imported modules. superContext is the "parent" lexical scope.
 */
public interface Context {
  @Nullable Var getLocal(@NotNull String name, Stmt.@NotNull Accessibility accessibility);

  default @Nullable Var getLocal(@NotNull String name) {
    return getLocal(name, Stmt.Accessibility.Private);
  }

  default @Nullable Var getLocal(@NotNull Seq<@NotNull String> path) {
    assert path.size() > 0; // should not happen
    return Option.of(getSubContextLocal(path.view().dropLast(1)))
      .flatMap(ctx -> Option.of(ctx.getLocal(path.last(), path.size() == 1 ? Stmt.Accessibility.Private : Stmt.Accessibility.Public)))
      .getOrNull();
  }

  boolean containsLocal(@NotNull String name);

  void forEachLocal(@NotNull BiConsumer<@NotNull String, @NotNull Tuple2<@NotNull Var, Stmt.@NotNull Accessibility>> f);

  default @Nullable Var get(@NotNull String name, @NotNull Stmt.Accessibility accessibility) {
    return Option.of(getLocal(name, accessibility))
      .getOrElse(() ->
        Option.of(getSuperContext())
          .map(sup -> sup.get(name, accessibility))
          .getOrNull());
  }

  default @Nullable Var get(@NotNull String name) {
    return get(name, Stmt.Accessibility.Private);
  }

  default @Nullable Var get(@NotNull Seq<@NotNull String> path) {
    return Option.of(getLocal(path))
      .getOrElse(() ->
        Option.of(getSuperContext())
          .map(sup -> sup.get(path))
          .getOrNull());
  }

  void unsafePutLocal(@NotNull String name, @NotNull Var ref, Stmt.@NotNull Accessibility accessibility);

  default void putLocal(@NotNull String name, @NotNull Var ref, Stmt.@NotNull Accessibility accessibility) {
    // TODO[xyr]: should report instead of throw
    if (containsLocal(name)) throw new IllegalStateException("Trying to add duplicate ref `" + name + "` to a context");
    unsafePutLocal(name, ref, accessibility);
  }

  boolean containsSubContextLocal(@NotNull String name);

  @Nullable Context getSubContextLocal(@NotNull String name, Stmt.@NotNull Accessibility accessibility);

  default @Nullable Context getSubContextLocal(@NotNull String name) {
    return getSubContextLocal(name, Stmt.Accessibility.Private);
  }

  default @Nullable Context getSubContextLocal(@NotNull Seq<@NotNull String> path, Stmt.@NotNull Accessibility accessibility) {
    if (path.size() == 0) return this;
    return Option.of(getSubContextLocal(path.first(), accessibility))
      .flatMap(ctx -> Option.of(ctx.getSubContextLocal(path.view().drop(1), Stmt.Accessibility.Public)))
      .getOrNull();
  }

  default @Nullable Context getSubContextLocal(@NotNull Seq<@NotNull String> path) {
    return getSubContextLocal(path, Stmt.Accessibility.Private);
  }

  default @Nullable Context getSubContext(@NotNull String name) {
    return Option.of(getSubContextLocal(name))
      .getOrElse(() ->
        Option.of(getSuperContext())
          .map(sup -> sup.getSubContext(name))
          .getOrNull());
  }

  default @Nullable Context getSubContext(@NotNull Seq<@NotNull String> path) {
    return Option.of(getSubContext(path.first()))
      .flatMap(ctx -> Option.of(ctx.getSubContextLocal(path.view().drop(1))))
      .getOrNull();
  }

  void unsafePutSubContextLocal(@NotNull String name, @NotNull Context ctx, Stmt.@NotNull Accessibility accessibility);

  default void putSubContextLocal(@NotNull String name, @NotNull Context ctx, Stmt.@NotNull Accessibility accessibility) {
    // TODO[xyr]: should report instead of throw
    if (containsSubContextLocal(name)) throw new IllegalStateException("Trying to add duplicate sub context `" + name + "` to a context");
    unsafePutSubContextLocal(name, ctx, accessibility);
  }

  @Nullable Context getSuperContext();

  void setSuperContext(@NotNull Context ctx);

  default @Nullable Context getTopContext() {
    return Option.of(getSuperContext())
      .map(Context::getTopContext)
      .getOrDefault(this);
  }
}

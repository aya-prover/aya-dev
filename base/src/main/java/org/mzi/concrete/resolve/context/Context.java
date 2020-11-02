// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.context;

import asia.kala.collection.Seq;
import asia.kala.control.Option;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;

/**
 * @author re-xyr
 * @implNote subContext and superContext are NOT dual concepts. subContexts are local or imported modules. superContext is the "parent" lexical scope.
 */
public interface Context {
  @Nullable Var getLocal(String name, boolean withPrivate);

  default @Nullable Var getLocal(String name) {
    return getLocal(name, true);
  }

  default @Nullable Var getLocal(Seq<String> path) {
    assert path.size() > 0; // should not happen
    return Option.of(getSubContextLocal(path.view().dropLast(1)))
      .flatMap(ctx -> Option.of(ctx.getLocal(path.last(), path.size() == 1)))
      .getOrNull();
  }

  boolean containsLocal(String name);

  default @Nullable Var get(String name) {
    return Option.of(getLocal(name))
      .getOrElse(() ->
        Option.of(getSuperContext())
          .map(sup -> sup.get(name))
          .getOrNull());
  }

  default @Nullable Var get(Seq<String> path) {
    return Option.of(getLocal(path))
      .getOrElse(() ->
        Option.of(getSuperContext())
          .map(sup -> sup.get(path))
          .getOrNull());
  }

  void unsafePutLocal(String name, Var ref, boolean isPublic);

  default void putLocal(String name, Var ref, boolean isPublic) {
    // TODO[xyr]: should report instead of throw
    if (containsLocal(name)) throw new IllegalStateException("Trying to add duplicate ref `" + name + "` to a context");
    unsafePutLocal(name, ref, isPublic);
  }

  boolean containsSubContextLocal(String name);

  @Nullable Context getSubContextLocal(String name, boolean withPrivate);

  default @Nullable Context getSubContextLocal(String name) {
    return getSubContextLocal(name, true);
  }

  default @Nullable Context getSubContextLocal(Seq<String> path, boolean withPrivate) {
    if (path.size() == 0) return this;
    return Option.of(getSubContextLocal(path.first(), withPrivate))
      .flatMap(ctx -> Option.of(ctx.getSubContextLocal(path.view().drop(1), false)))
      .getOrNull();
  }

  default @Nullable Context getSubContextLocal(Seq<String> path) {
    return getSubContextLocal(path, true);
  }

  default @Nullable Context getSubContext(String name) {
    return Option.of(getSubContextLocal(name))
      .getOrElse(() ->
        Option.of(getSuperContext())
          .map(sup -> sup.getSubContext(name))
          .getOrNull());
  }

  default @Nullable Context getSubContext(Seq<String> path) {
    return Option.of(getSubContext(path.first()))
      .flatMap(ctx -> Option.of(ctx.getSubContextLocal(path.view().drop(1))))
      .getOrNull();
  }

  void unsafePutSubContextLocal(String name, Context ctx, boolean isPublic);

  default void putSubContextLocal(String name, Context ctx, boolean isPublic) {
    // TODO[xyr]: should report instead of throw
    if (containsSubContextLocal(name)) throw new IllegalStateException("Trying to add duplicate sub context `" + name + "` to a context");
    unsafePutSubContextLocal(name, ctx, isPublic);
  }

  @Nullable Context getSuperContext();

  void setSuperContext(Context ctx);

  default @Nullable Context getTopContext() {
    return Option.of(getSuperContext())
      .map(Context::getTopContext)
      .getOrDefault(this);
  }
}

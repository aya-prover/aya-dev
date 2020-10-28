// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.context;

import asia.kala.collection.Seq;
import asia.kala.control.Option;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;

/**
 * @author re-xyr
 */
public interface Context {
  @Nullable Var getLocal(String name);

  default @Nullable Var getLocal(Seq<String> path) {
    assert path.size() > 0; // should not happen
    return Option.of(getSubContextLocal(path.view().dropLast(1)))
      .flatMap(ctx -> Option.of(ctx.getLocal(path.last())))
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

  void unsafePutLocal(String name, Var ref);

  default void putLocal(String name, Var ref) {
    // TODO[xyr]: should report instead of throw
    if (containsLocal(name)) throw new IllegalStateException("Trying to add duplicate ref `" + name + "` to a context");
    unsafePutLocal(name, ref);
  }

  boolean containsSubContextLocal(String name);

  @Nullable Context getSubContextLocal(String name);

  default @Nullable Context getSubContextLocal(Seq<String> path) {
    if (path.size() == 0) return this;
    return Option.of(getSubContextLocal(path.first()))
      .flatMap(ctx -> Option.of(ctx.getSubContextLocal(path.view().drop(1))))
      .getOrNull();
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

  void unsafePutSubContextLocal(String name, Context ctx);

  default void putSubContextLocal(String name, Context ctx) {
    // TODO[xyr]: should report instead of throw
    if (containsSubContextLocal(name)) throw new IllegalStateException("Trying to add duplicate sub context `" + name + "` to a context");
    unsafePutSubContextLocal(name, ctx);
  }

  default void linkSubContext(String name, Context ctx) {
    ctx.setSuperContext(this);
    putSubContextLocal(name, ctx);
  }

  @Nullable Context getSuperContext();

  void setSuperContext(Context ctx);

  default @Nullable Context getTopContext() {
    return Option.of(getSuperContext())
      .map(Context::getTopContext)
      .getOrDefault(this);
  }
}

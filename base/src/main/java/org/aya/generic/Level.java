// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.generic;

import org.aya.api.ref.Var;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @param <V> either {@link org.aya.api.ref.LevelGenVar} (which means level vars in concrete)
 *            or {@link org.aya.core.sort.Sort.LvlVar} (which means levels in core).
 *            Used only in {@link Reference}.
 * @author ice1000
 * @see org.aya.concrete.Expr.UnivExpr
 * @see org.aya.core.sort.Sort
 */
@Debug.Renderer(text = "toDoc().debugRender()")
public sealed interface Level<V extends Var> extends Docile {
  @NotNull Level<V> lift(int n);
  <Lvl extends Var> @NotNull Level<Lvl> map(@NotNull Function<V, Lvl> map);

  /**
   * Unlike {@link Reference}, this one is the implicit polymorphic level.
   * It is related to the underlying definition and are eliminated during tycking (becomes {@link Reference}).
   */
  record Polymorphic<V extends Var>(int lift) implements Level<V> {
    @Override public @NotNull Level<V> lift(int n) {
      return new Polymorphic<>(lift + n);
    }

    @Override public @NotNull <Lvl extends Var> Level<Lvl> map(@NotNull Function<V, Lvl> map) {
      return new Polymorphic<>(lift);
    }

    @Override public @NotNull Doc toDoc() {
      return levelDoc(lift, "lp");
    }
  }

  static @NotNull Doc levelDoc(int lift, String name) {
    return Doc.plain(name + (lift > 0 ? " + " + lift : ""));
  }

  record Infinity<V extends Var>() implements Level<V> {
    @Override public @NotNull Level<V> lift(int n) {
      return this;
    }

    @Override public @NotNull <Lvl extends Var> Level<Lvl> map(@NotNull Function<V, Lvl> map) {
      return new Infinity<>();
    }

    @Override public @NotNull Doc toDoc() {
      return Doc.plain("w");
    }
  }

  record Constant<V extends Var>(int value) implements Level<V> {
    @Override public @NotNull Level<V> lift(int n) {
      return new Constant<>(value + n);
    }

    @Override public @NotNull <Lvl extends Var> Level<Lvl> map(@NotNull Function<V, Lvl> map) {
      return new Constant<>(value);
    }

    @Override public @NotNull Doc toDoc() {
      return Doc.plain(String.valueOf(value));
    }
  }

  record Reference<V extends Var>(@NotNull V ref, int lift) implements Level<V> {
    public Reference(@NotNull V ref) {
      this(ref, 0);
    }

    @Override public @NotNull <Lvl extends Var> Level<Lvl> map(@NotNull Function<V, Lvl> map) {
      return new Reference<>(map.apply(ref), lift);
    }

    @Override public @NotNull Level<V> lift(int n) {
      return new Reference<>(ref, lift + n);
    }

    @Override public @NotNull Doc toDoc() {
      return levelDoc(lift, ref.name());
    }
  }

}

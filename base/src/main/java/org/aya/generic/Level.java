// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.generic;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.LevelGenVar;
import org.aya.api.ref.Var;
import org.aya.distill.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

/**
 * @param <V> either {@link org.aya.api.ref.LevelGenVar} (which means level vars in concrete)
 *            or {@link org.aya.core.sort.Sort.LvlVar} (which means levels in core).
 *            Used only in {@link Reference}.
 * @author ice1000
 * @see org.aya.concrete.Expr.UnivExpr
 * @see org.aya.core.sort.Sort
 */
public sealed interface Level<V extends Var> extends AyaDocile {
  @NotNull Level<V> lift(int n);

  /**
   * Unlike {@link Reference}, this one is the implicit polymorphic level.
   * It is related to the underlying definition and are eliminated during tycking (becomes {@link Reference}).
   */
  record Polymorphic(int lift) implements Level<LevelGenVar> {
    @Override public @NotNull Polymorphic lift(int n) {
      return new Polymorphic(lift + n);
    }

    @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return levelDoc(lift, "lp");
    }
  }

  record Maximum(ImmutableSeq<Level<LevelGenVar>> among) implements Level<LevelGenVar> {
    @Override public @NotNull Maximum lift(int n) {
      return new Maximum(among.map(l -> l.lift(n)));
    }

    @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return Doc.parened(Doc.sep(among.view()
        .map(l -> l.toDoc(options))
        .prepended(Doc.styled(CoreDistiller.KEYWORD, "max"))
        .toImmutableSeq()));
    }
  }

  static @NotNull Doc levelDoc(int lift, String name) {
    return Doc.plain(name + (lift > 0 ? " + " + lift : ""));
  }

  record Infinity<V extends Var>() implements Level<V> {
    @Override public @NotNull Level<V> lift(int n) {
      return this;
    }

    @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return Doc.plain("w");
    }
  }

  record Constant<V extends Var>(int value) implements Level<V> {
    @Override public @NotNull Level<V> lift(int n) {
      return new Constant<>(value + n);
    }

    @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return Doc.plain(String.valueOf(value));
    }
  }

  record Reference<V extends Var>(@NotNull V ref, int lift) implements Level<V> {
    public Reference(@NotNull V ref) {
      this(ref, 0);
    }

    @Override public @NotNull Level<V> lift(int n) {
      return new Reference<>(ref, lift + n);
    }

    @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return levelDoc(lift, ref.name());
    }
  }
}

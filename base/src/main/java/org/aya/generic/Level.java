// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.ref.Var;
import org.aya.distill.BaseDistiller;
import org.aya.generic.ref.PreLevelVar;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.NotNull;

/**
 * @param <V> either {@link PreLevelVar} (which means level vars in concrete)
 *            or {@link org.aya.core.sort.Sort.LvlVar} (which means levels in core).
 *            Used only in {@link Reference}.
 * @author ice1000
 * @see org.aya.concrete.Expr.UnivExpr
 * @see org.aya.core.sort.Sort
 */
public sealed interface Level<V extends Var> extends Docile {
  @NotNull Level<V> lift(int n);

  /**
   * Unlike {@link Reference}, this one is the implicit polymorphic level.
   * It is related to the underlying definition and are eliminated during tycking (becomes {@link Reference}).
   */
  record Polymorphic(int lift) implements Level<PreLevelVar> {
    @Override public @NotNull Polymorphic lift(int n) {
      return new Polymorphic(lift + n);
    }

    @Override public @NotNull Doc toDoc() {
      return levelDoc(lift, "lp");
    }
  }

  record Maximum(ImmutableSeq<Level<PreLevelVar>> among) implements Level<PreLevelVar> {
    @Override public @NotNull Maximum lift(int n) {
      return new Maximum(among.map(l -> l.lift(n)));
    }

    @Override public @NotNull Doc toDoc() {
      return Doc.parened(Doc.sep(among.view()
        .map(Docile::toDoc)
        .prepended(Doc.styled(BaseDistiller.KEYWORD, "max"))
        .toImmutableSeq()));
    }
  }

  static @NotNull Doc levelDoc(int lift, String name) {
    if (lift > 0) return Doc.parened(Doc.plain(name + " + " + lift));
    return Doc.plain(name);
  }

  record Constant<V extends Var>(int value) implements Level<V> {
    @Override public @NotNull Level<V> lift(int n) {
      return new Constant<>(value + n);
    }

    @Override public @NotNull Doc toDoc() {
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

    @Override public @NotNull Doc toDoc() {
      return levelDoc(lift, ref.name());
    }
  }
}

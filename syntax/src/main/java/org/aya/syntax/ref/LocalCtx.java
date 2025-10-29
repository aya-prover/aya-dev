// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableTreeSeq;
import kala.collection.mutable.MutableArrayList;
import kala.collection.mutable.MutableLinkedHashMap;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.Term;
import org.aya.util.Scoped;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public sealed interface LocalCtx extends Scoped<LocalVar, @Closed Term, LocalCtx> permits SeqLocalCtx, MapLocalCtx {
  boolean isEmpty();
  int size();
  @Contract(value = "_ -> new", pure = true)
  @NotNull LocalCtx map(UnaryOperator<Term> mapper);

  @NotNull SeqView<LocalVar> extractLocal();
  default @NotNull SeqView<LocalVar> extract() {
    SeqView<LocalVar> parentView = parent() == null ? SeqView.empty() : parent().extract();
    return parentView.concat(extractLocal());
  }

  @NotNull LocalCtx clone();
  @Override default @NotNull LocalCtx self() { return this; }

  /// @return using empty maps because we either don't use it (we're using let instead) or we
  /// insert to it a lot
  @Override @Contract("-> new") default @NotNull LocalCtx derive() {
    return new MapLocalCtx(new MutableLinkedHashMap<>(0), MutableArrayList.create(0), this);
  }
  @Contract("_,_->new") default @NotNull LocalCtx derive1(@NotNull LocalVar var, @NotNull Term type) {
    return new SeqLocalCtx(ImmutableTreeSeq.of(type), ImmutableTreeSeq.of(var), this);
  }
}

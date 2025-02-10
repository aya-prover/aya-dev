// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.ImmutableTreeSeq;
import kala.control.Option;
import org.aya.syntax.core.term.Term;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

public record SeqLocalCtx(
  @NotNull ImmutableSeq<Term> type,
  @NotNull ImmutableSeq<LocalVar> var,
  @Override @Nullable LocalCtx parent
) implements LocalCtx {
  @Override public boolean isEmpty() { return false; }
  @Override public int size() {
    return 1 + (parent == null ? 0 : parent.size());
  }
  @Override public @NotNull LocalCtx map(UnaryOperator<Term> mapper) {
    return new SeqLocalCtx(ImmutableTreeSeq.from(type.view().map(mapper)), var,
      parent == null ? null : parent.map(mapper));
  }
  @Override public @NotNull SeqView<LocalVar> extractLocal() { return var.view(); }
  @Override public @NotNull LocalCtx clone() {
    if (parent == null) return this;
    return new SeqLocalCtx(type, var, parent.clone());
  }
  @Override public @NotNull Option<Term> getLocal(@NotNull LocalVar key) {
    var index = var.indexOf(key);
    if (index >= 0) return Option.some(type.get(index));
    return parent == null ? Option.none() : parent.getLocal(key);
  }
  @Override public void putLocal(@NotNull LocalVar key, @NotNull Term value) {
    throw new Panic("LocalCtx1 is immutable");
  }
  @Override public @NotNull LocalCtx derive1(@NotNull LocalVar var1, @NotNull Term type1) {
    return new SeqLocalCtx(type.prepended(type1), var.prepended(var1), this);
  }
}

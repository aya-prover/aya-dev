// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import kala.collection.SeqView;
import kala.control.Option;
import org.aya.syntax.core.term.Term;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

public record LocalCtx1(
  @NotNull Term type,
  @NotNull LocalVar var,
  @Override @Nullable LocalCtx parent
) implements LocalCtx {
  @Override public boolean isEmpty() { return false; }
  @Override public int size() {
    return 1 + (parent == null ? 0 : parent.size());
  }
  @Override public @NotNull LocalCtx map(UnaryOperator<Term> mapper) {
    return new LocalCtx1(mapper.apply(type), var, parent == null ? null : parent.map(mapper));
  }
  @Override public @NotNull SeqView<LocalVar> extract() {
    SeqView<LocalVar> parentView = parent == null ? SeqView.empty() : parent.extract();
    return parentView.concat(SeqView.of(var));
  }
  @Override public @NotNull LocalCtx clone() {
    return new LocalCtx1(type, var, parent == null ? null : parent.clone());
  }
  @Override public @NotNull Option<Term> getLocal(@NotNull LocalVar key) {
    return key.equals(var) ? Option.some(type) : parent == null ? Option.none() : parent.getLocal(key);
  }
  @Override public void putLocal(@NotNull LocalVar key, @NotNull Term value) {
    throw new Panic("LocalCtx1 is immutable");
  }
}

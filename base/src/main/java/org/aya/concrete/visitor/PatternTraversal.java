// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import kala.collection.SeqLike;
import kala.tuple.Unit;
import org.aya.concrete.Pattern;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public interface PatternTraversal<P> {
  static void visit(@NotNull Consumer<Pattern> consumer, SeqLike<Pattern> patterns) {
    new PatternTraversal<Unit>() {
      @Override public void visitPattern(@NotNull Pattern pattern, Unit pp) {
        consumer.accept(pattern);
        PatternTraversal.super.visitPattern(pattern, pp);
      }
    }.visitPatterns(patterns, Unit.unit());
  }

  default void visitPattern(@NotNull Pattern pattern, P pp) {
    switch (pattern) {
      case Pattern.BinOpSeq seq -> visitBinOpPattern(seq, pp);
      case Pattern.Ctor ctor -> visitPatterns(ctor.params(), pp);
      case Pattern.Tuple tup -> visitPatterns(tup.patterns(), pp);
      default -> {}
    }
  }

  default void visitBinOpPattern(@NotNull Pattern.BinOpSeq seq, P pp) {
    visitPatterns(seq.seq(), pp);
  }
  default void visitPatterns(SeqLike<Pattern> patterns, P pp) {
    patterns.forEach(p -> visitPattern(p, pp));
  }
}

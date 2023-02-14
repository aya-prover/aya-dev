// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.tyck;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableDeque;
import kala.collection.mutable.MutableList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Multi-case trees.
 *
 * @author ice1000
 */
public sealed interface MCT<Term> {
  static <Term, Pat> @NotNull ImmutableSeq<SubPats<Pat>> extract(
    PatClass<Term> pats, @NotNull ImmutableSeq<SubPats<Pat>> subPatsSeq) {
    return pats.contents().map(subPatsSeq::get);
  }
  /**
   * Helper method to avoid stack being too deep and fuel being consumed for distinct patterns.
   *
   * @param subPatsSeq should be of the same length, and should <strong>not</strong> be empty.
   * @param classifier turn a set of sub-patterns into an MCT.
   * @return pattern classes
   */
  static @NotNull <Term, Pat, Param> MCT<Term> classify(
    @NotNull SeqView<Param> telescope,
    @NotNull ImmutableSeq<SubPats<Pat>> subPatsSeq,
    @NotNull BiFunction<SeqView<Param>, ImmutableSeq<SubPats<Pat>>, MCT<Term>> classifier
  ) {
    while (telescope.isNotEmpty()) {
      var res = classifier.apply(telescope, subPatsSeq);
      if (res != null) return res;
      else {
        telescope = telescope.drop(1);
        subPatsSeq = subPatsSeq.map(SubPats::drop);
      }
    }
    // Done
    return new Leaf<>(subPatsSeq.map(SubPats::ix));
  }

  default @NotNull ImmutableSeq<PatClass<Term>> toSeq() {
    var buffer = MutableList.<PatClass<Term>>create();
    forEach(buffer::append);
    return buffer.toImmutableSeq();
  }
  default void forEach(@NotNull Consumer<PatClass<Term>> f) {
    var queue = MutableDeque.<MCT<Term>>create();
    queue.enqueue(this);
    while (queue.isNotEmpty()) switch (queue.dequeue()) {
      case PatClass<Term> leaf -> f.accept(leaf);
      case Node<Term> node -> node.children.forEach(queue::enqueue);
    }
  }
  @NotNull MCT<Term> map(@NotNull Function<PatClass<Term>, PatClass<Term>> f);
  @NotNull MCT<Term> flatMap(@NotNull Function<PatClass<Term>, MCT<Term>> f);

  sealed interface PatClass<Term> extends MCT<Term> {
    @NotNull ImmutableSeq<Integer> contents();

    @NotNull MCT<Term> propagate(@NotNull MCT<Term> mct);

    @Override default @NotNull PatClass<Term> map(@NotNull Function<PatClass<Term>, PatClass<Term>> f) {
      return f.apply(this);
    }
    @Override default @NotNull MCT<Term> flatMap(@NotNull Function<PatClass<Term>, MCT<Term>> f) {
      return f.apply(this);
    }
  }

  record Leaf<Term>(@NotNull ImmutableSeq<Integer> contents) implements PatClass<Term> {
    @Override public @NotNull MCT<Term> propagate(@NotNull MCT<Term> mct) {
      return mct;
    }
  }

  record Error<Term>(
    @NotNull ImmutableSeq<Integer> contents,
    @NotNull Object errorMessage
  ) implements PatClass<Term> {
    @Override public @NotNull MCT<Term> propagate(@NotNull MCT<Term> mct) {
      return mct.map(newClz -> new Error<>(newClz.contents(), errorMessage));
    }
  }

  record Node<Term>(@NotNull Term type, @NotNull ImmutableSeq<MCT<Term>> children) implements MCT<Term> {
    @Override public @NotNull Node<Term> map(@NotNull Function<PatClass<Term>, PatClass<Term>> f) {
      return new Node<>(type, children.map(child -> child.map(f)));
    }

    @Override public @NotNull Node<Term> flatMap(@NotNull Function<PatClass<Term>, MCT<Term>> f) {
      return new Node<>(type, children.map(child -> child.flatMap(f)));
    }
  }

  record SubPats<Pat>(@NotNull SeqView<Pat> pats, int ix) {
    @Contract(pure = true) public @NotNull Pat head() {
      return pats.first();
    }

    @Contract(pure = true) public @NotNull SubPats<Pat> drop() {
      return new SubPats<>(pats.drop(1), ix);
    }
  }
}

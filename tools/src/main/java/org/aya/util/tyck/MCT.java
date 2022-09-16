// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.tyck;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
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
public sealed interface MCT<Term, Err> {
  static <Term, Pat, Err> @NotNull ImmutableSeq<SubPats<Pat>> extract(
    PatClass<Term, Err> pats, @NotNull ImmutableSeq<SubPats<Pat>> subPatsSeq) {
    return pats.contents().map(subPatsSeq::get);
  }
  /**
   * Helper method to avoid stack being too deep and fuel being consumed for distinct patterns.
   *
   * @param subPatsSeq should be of the same length, and should <strong>not</strong> be empty.
   * @param classifier turn a set of sub-patterns into an MCT.
   * @return pattern classes
   */
  static @NotNull <Term, Err, Pat, Param> MCT<Term, Err> classify(
    @NotNull SeqView<Param> telescope,
    @NotNull ImmutableSeq<SubPats<Pat>> subPatsSeq,
    @NotNull BiFunction<SeqView<Param>, ImmutableSeq<SubPats<Pat>>, MCT<Term, Err>> classifier
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

  default @NotNull ImmutableSeq<PatClass<Term, Err>> toSeq() {
    var buffer = MutableList.<PatClass<Term, Err>>create();
    forEach(buffer::append);
    return buffer.toImmutableSeq();
  }
  SeqView<PatClass<Term, Err>> view();
  default void forEach(@NotNull Consumer<PatClass<Term, Err>> f) {
    view().forEach(f);
  }
  @NotNull MCT<Term, Err> map(@NotNull Function<PatClass<Term, Err>, PatClass<Term, Err>> f);
  @NotNull MCT<Term, Err> flatMap(@NotNull Function<PatClass<Term, Err>, MCT<Term, Err>> f);

  sealed interface PatClass<Term, Err> extends MCT<Term, Err> {
    @NotNull ImmutableSeq<Integer> contents();

    @NotNull MCT<Term, Err> propagate(@NotNull MCT<Term, Err> mct);

    @Override default @NotNull PatClass<Term, Err> map(@NotNull Function<PatClass<Term, Err>, PatClass<Term, Err>> f) {
      return f.apply(this);
    }

    @Override default SeqView<PatClass<Term, Err>> view() {
      return SeqView.of(this);
    }
    @Override default @NotNull MCT<Term, Err> flatMap(@NotNull Function<PatClass<Term, Err>, MCT<Term, Err>> f) {
      return f.apply(this);
    }
  }

  record Leaf<Term, Err>(@NotNull ImmutableSeq<Integer> contents) implements PatClass<Term, Err> {
    @Override public @NotNull MCT<Term, Err> propagate(@NotNull MCT<Term, Err> mct) {
      return mct;
    }
  }

  record Error<Term, Err>(
    @NotNull ImmutableSeq<Integer> contents,
    @NotNull Err errorMessage
  ) implements PatClass<Term, Err> {
    @Override public @NotNull MCT<Term, Err> propagate(@NotNull MCT<Term, Err> mct) {
      return mct.map(newClz -> new Error<>(newClz.contents(), errorMessage));
    }
  }

  record Node<Term, Err>(@NotNull Term type, @NotNull ImmutableSeq<MCT<Term, Err>> children) implements MCT<Term, Err> {
    @Override public SeqView<PatClass<Term, Err>> view() {
      return children.view().flatMap(MCT::view);
    }

    @Override public @NotNull Node<Term, Err> map(@NotNull Function<PatClass<Term, Err>, PatClass<Term, Err>> f) {
      return new Node<>(type, children.map(child -> child.map(f)));
    }

    @Override public @NotNull Node<Term, Err> flatMap(@NotNull Function<PatClass<Term, Err>, MCT<Term, Err>> f) {
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

// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import org.aya.concrete.Pattern;
import org.aya.core.pat.Pat;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Multi-case trees.
 *
 * @author ice1000
 */
public sealed interface MCT {
  static @NotNull MCT mapClass(@NotNull PatClass pat, @NotNull MCT classes) {
    return switch (pat) {
      case Leaf leaf -> classes;
      case Error error -> classes.map(newClz -> new Error(newClz.contents(), error.errorMessage));
    };
  }

  static @NotNull ImmutableSeq<SubPats> extract(PatClass pats, @NotNull ImmutableSeq<SubPats> subPatsSeq) {
    return pats.contents().map(subPatsSeq::get);
  }

  default @NotNull ImmutableSeq<PatClass> toSeq() {
    var buffer = DynamicSeq.<PatClass>create();
    forEach(buffer::append);
    return buffer.toImmutableSeq();
  }
  void forEach(@NotNull Consumer<PatClass> f);
  @NotNull MCT map(@NotNull Function<PatClass, PatClass> f);
  @NotNull MCT flatMap(@NotNull Function<PatClass, MCT> f);

  sealed interface PatClass extends MCT {
    @NotNull ImmutableSeq<Integer> contents();

    @Override default void forEach(@NotNull Consumer<PatClass> f) {
      f.accept(this);
    }

    @Override default @NotNull PatClass map(@NotNull Function<PatClass, PatClass> f) {
      return f.apply(this);
    }

    @Override default @NotNull MCT flatMap(@NotNull Function<PatClass, MCT> f) {
      return f.apply(this);
    }

  }

  record Leaf(@NotNull ImmutableSeq<Integer> contents) implements PatClass {
  }

  record Error(
    @NotNull ImmutableSeq<Integer> contents,
    @NotNull ImmutableSeq<Pattern> errorMessage
  ) implements PatClass {
  }

  record Node(@NotNull ImmutableSeq<MCT> children) implements MCT {
    @Override public void forEach(@NotNull Consumer<PatClass> f) {
      children.forEach(child -> child.forEach(f));
    }

    @Override public @NotNull Node map(@NotNull Function<PatClass, PatClass> f) {
      return new Node(children.map(child -> child.map(f)));
    }

    @Override public @NotNull Node flatMap(@NotNull Function<PatClass, MCT> f) {
      return new Node(children.map(child -> child.flatMap(f)));
    }
  }

  record CaseError() implements MCT {
    @Override public void forEach(@NotNull Consumer<PatClass> f) {
    }

    @Override public @NotNull CaseError map(@NotNull Function<PatClass, PatClass> f) {
      return this;
    }

    @Override public @NotNull MCT flatMap(@NotNull Function<PatClass, MCT> f) {
      return this;
    }
  }

  record SubPats(@NotNull SeqView<Pat> pats, int ix) {
    @Contract(pure = true) public @NotNull Pat head() {
      // This 'inline' is actually a 'dereference'
      return pats.first().inline();
    }

    @Contract(pure = true) public @NotNull SubPats drop() {
      return new SubPats(pats.drop(1), ix);
    }
  }
}

// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.telescope;

import kala.collection.ArraySeq;
import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.range.primitive.IntRange;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.ErrorTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.PiTerm;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * Index-safe telescope
 */
public interface AbstractTele {
  /**
   * @param teleArgs the arguments before {@param i}, for constructor, it also contains the arguments to the data
   */
  default @NotNull Term telescope(int i, Term[] teleArgs) {
    return telescope(i, ArraySeq.wrap(teleArgs));
  }

  /**
   * Get the type of {@param i}-th (count from {@code 0}) parameter.
   *
   * @param teleArgs the arguments to the former parameters
   * @return the type of {@param i}-th parameter.
   */
  @NotNull Term telescope(int i, Seq<Term> teleArgs);

  /**
   * Get the result of this signature
   * @param teleArgs the arguments to all parameters.
   */
  @NotNull Term result(Seq<Term> teleArgs);

  /**
   * Return the amount of parameters.
   */
  int telescopeSize();

  /**
   * Return the licit of {@param i}-th parameter.
   * @return true if explicit
   */
  boolean telescopeLicit(int i);

  /**
   * Get the name of {@param i}-th parameter.
   */
  @NotNull String telescopeName(int i);

  /**
   * Get all information of {@param i}-th parameter
   * @see #telescope
   * @see #telescopeName
   * @see #telescopeLicit
   */
  default @NotNull Param telescopeRich(int i, Term... teleArgs) {
    return new Param(telescopeName(i), telescope(i, teleArgs), telescopeLicit(i));
  }

  default @NotNull Term result(Term... teleArgs) {
    return result(ArraySeq.wrap(teleArgs));
  }

  default @NotNull SeqView<String> namesView() {
    return ImmutableIntSeq.from(IntRange.closedOpen(0, telescopeSize()))
      .view().mapToObj(this::telescopeName);
  }

  default @NotNull Term makePi() {
    return new PiBuilder(this).make(0, ImmutableSeq.empty());
  }

  record PiBuilder(AbstractTele telescope) {
    public @NotNull Term make(int i, ImmutableSeq<Term> args) {
      return i == telescope.telescopeSize() ? telescope.result(args) :
        new PiTerm(telescope.telescope(i, args), new Closure.Jit(arg ->
          make(i + 1, args.appended(arg))));
    }
  }

  default void checkIndex(int i) {
    assert i < telescopeSize();
  }

  default void checkResult(@NotNull Seq<Term> teleArgs) {
    assert teleArgs.size() == telescopeSize();
  }

  default @NotNull AbstractTele lift(int i) { return i == 0 ? this : new Lift(this, i); }
  default @NotNull AbstractTele prefix(int i) {
    return i == telescopeSize() ? this : new Slice(this, i);
  }

  /**
   * Default implementation of {@link AbstractTele}
   *
   * @param telescope bound parameters, that is, the later parameter can refer to the former parameters
   *                  by {@link org.aya.syntax.core.term.LocalTerm}
   * @param result    bound result
   */
  record Locns(@NotNull ImmutableSeq<Param> telescope, @NotNull Term result) implements AbstractTele {
    @Override public int telescopeSize() { return telescope.size(); }
    @Override public boolean telescopeLicit(int i) { return telescope.get(i).explicit(); }
    @Override public @NotNull String telescopeName(int i) { return telescope.get(i).name(); }
    @Override public @NotNull Term telescope(int i, Seq<Term> teleArgs) {
      return telescope.get(i).type().instantiateTele(teleArgs.sliceView(0, i));
    }
    @Override public @NotNull Term result(Seq<Term> teleArgs) {
      assert teleArgs.size() == telescopeSize();
      return result.instantiateTele(teleArgs.view());
    }
    @Override public @NotNull SeqView<String> namesView() {
      return telescope.view().map(Param::name);
    }
  }

  record Lift(
    @NotNull AbstractTele signature,
    int lift
  ) implements AbstractTele {
    @Override public int telescopeSize() { return signature.telescopeSize(); }
    @Override public boolean telescopeLicit(int i) { return signature.telescopeLicit(i); }
    @Override public @NotNull String telescopeName(int i) { return signature.telescopeName(i); }
    @Override public @NotNull Term telescope(int i, Seq<Term> teleArgs) {
      return signature.telescope(i, teleArgs).elevate(lift);
    }
    @Override public @NotNull Term result(Seq<Term> teleArgs) {
      return signature.result(teleArgs).elevate(lift);
    }
    @Override public @NotNull AbstractTele lift(int i) { return new Lift(signature, lift + i); }
    @Override public @NotNull SeqView<String> namesView() { return signature.namesView(); }
  }

  /**
   * Takes first {@param telescopeSize} parameters of {@param signature}. {@link #result} is not available
   */
  record Slice(
    @NotNull AbstractTele signature,
    @Override int telescopeSize
  ) implements AbstractTele {
    @Override public boolean telescopeLicit(int i) { return signature.telescopeLicit(i); }
    @Override public @NotNull String telescopeName(int i) { return signature.telescopeName(i); }
    @Override public @NotNull Term telescope(int i, Seq<Term> teleArgs) {
      return signature.telescope(i, teleArgs);
    }
    @Override public @NotNull Term result(Seq<Term> teleArgs) {
      return ErrorTerm.DUMMY;
    }
    @Override public @NotNull AbstractTele prefix(int i) {
      return new Slice(signature, i);
    }
    @Override public @NotNull SeqView<String> namesView() {
      return signature.namesView().sliceView(0, telescopeSize);
    }
  }

  /**
   * Apply first {@code args.size()} parameters with {@param args} of {@param signature}
   */
  record Apply(
    @NotNull AbstractTele signature,
    @NotNull ImmutableSeq<Term> args
  ) implements AbstractTele {
    public Apply {
      assert args.size() <= signature.telescopeSize();
    }

    @Override
    public @NotNull Term telescope(int i, Seq<Term> teleArgs) {
      checkIndex(i);
      return signature.telescope(telescopeSize() + i, args.appendedAll(teleArgs));
    }

    @Override
    public @NotNull Term result(Seq<Term> teleArgs) {
      checkResult(teleArgs);
      return signature.result(args.appendedAll(teleArgs));
    }

    @Override
    public int telescopeSize() {
      return signature.telescopeSize() - args.size();
    }

    @Override
    public boolean telescopeLicit(int i) {
      checkIndex(i);
      return signature.telescopeLicit(telescopeSize() + i);
    }

    @Override
    public @NotNull String telescopeName(int i) {
      checkIndex(i);
      return signature.telescopeName(telescopeSize() + i);
    }
  }
}

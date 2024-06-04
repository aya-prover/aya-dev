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

public interface AbstractTele {
  /**
   * @param teleArgs the arguments before {@param i}, for constructor, it also contains the arguments to the data
   */
  default @NotNull Term telescope(int i, Term[] teleArgs) {
    return telescope(i, ArraySeq.wrap(teleArgs));
  }

  @NotNull Term telescope(int i, Seq<Term> teleArgs);
  @NotNull Term result(Seq<Term> teleArgs);
  int telescopeSize();
  boolean telescopeLicit(int i);
  @NotNull String telescopeName(int i);
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
  default @NotNull AbstractTele lift(int i) { return i == 0 ? this : new Lift(this, i); }
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
  record Locns(
    @NotNull ImmutableSeq<Param> telescope,
    @NotNull Term result
  ) implements AbstractTele {
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
  default @NotNull AbstractTele prefix(int i) {
    return i == telescopeSize() ? this : new Slice(this, i);
  }
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
}

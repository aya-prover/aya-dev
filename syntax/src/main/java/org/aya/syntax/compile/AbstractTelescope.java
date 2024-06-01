// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.PiTerm;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public interface AbstractTelescope {
  /**
   * @param teleArgs the arguments before {@param i}, for constructor, it also contains the arguments to the data
   */
  default @NotNull Term telescope(int i, Term[] teleArgs) {
    return telescope(i, ImmutableArray.Unsafe.wrap(teleArgs));
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
    return result(ImmutableArray.Unsafe.wrap(teleArgs));
  }

  default @NotNull Term makePi() {
    return new PiBuilder(this).make(0, ImmutableSeq.empty());
  }
  record PiBuilder(AbstractTelescope telescope) {
    public @NotNull Term make(int i, ImmutableSeq<Term> args) {
      return i == telescope.telescopeSize() ? telescope.result(args) :
        new PiTerm(telescope.telescope(i, args), new Closure.Jit(arg ->
          make(i + 1, args.appended(arg))));
    }
  }
}

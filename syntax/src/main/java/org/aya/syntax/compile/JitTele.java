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

/**
 * A Jit telescope, which is efficient when instantiating parameters/result, but not friendly with DeBruijn Index.
 */
public abstract class JitTele {
  public final int telescopeSize;
  public final boolean[] telescopeLicit;
  public final String[] telescopeNames;

  /**
   * @param teleArgs the arguments before {@param i}, for constructor, it also contains the arguments to the data
   */
  public final @NotNull Term telescope(int i, Term[] teleArgs) {
    return telescope(i, ImmutableArray.Unsafe.wrap(teleArgs));
  }
  public abstract @NotNull Term telescope(int i, Seq<Term> teleArgs);
  public Param telescopeRich(int i, Term... teleArgs) {
    return new Param(telescopeNames[i], telescope(i, teleArgs), telescopeLicit[i]);
  }

  public final @NotNull Term result(Term... teleArgs) {
    return result(ImmutableArray.Unsafe.wrap(teleArgs));
  }
  public abstract @NotNull Term result(Seq<Term> teleArgs);

  protected JitTele(int telescopeSize, boolean[] telescopeLicit, String[] telescopeNames) {
    this.telescopeSize = telescopeSize;
    this.telescopeLicit = telescopeLicit;
    this.telescopeNames = telescopeNames;
  }

  public @NotNull Term makePi() {
    return new PiBuilder().make(0, ImmutableSeq.empty());
  }

  private class PiBuilder {
    public @NotNull Term make(int i, ImmutableSeq<Term> args) {
      return i == telescopeSize ? result(args) :
        new PiTerm(telescope(i, args), new Closure.Jit(arg -> make(i + 1, args.appended(arg))));
    }
  }

  public static class LocallyNameless extends JitTele {
    public final ImmutableSeq<Param> telescope;
    public final Term result;
    public LocallyNameless(ImmutableSeq<Param> telescope, Term result) {
      super(telescope.size(), new boolean[telescope.size()], new String[telescope.size()]);
      this.result = result;
      for (int i = 0; i < telescope.size(); i++) {
        telescopeLicit[i] = telescope.get(i).explicit();
        telescopeNames[i] = telescope.get(i).name();
      }
      this.telescope = telescope;
    }
    @Override public @NotNull Term telescope(int i, Seq<Term> teleArgs) {
      return telescope.get(i).type().instantiateTele(teleArgs.sliceView(0, i));
    }
    @Override public @NotNull Term result(Seq<Term> teleArgs) {
      assert teleArgs.size() == telescopeSize;
      return result.instantiateTele(teleArgs.view());
    }
  }
}

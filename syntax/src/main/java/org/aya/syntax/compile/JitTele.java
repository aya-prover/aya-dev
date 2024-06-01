// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.term.ErrorTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * A Jit telescope, which is efficient when instantiating parameters/result, but not friendly with DeBruijn Index.
 */
public abstract class JitTele implements AbstractTelescope {
  public final int telescopeSize;
  public final boolean[] telescopeLicit;
  public final String[] telescopeNames;

  protected JitTele(int telescopeSize, boolean[] telescopeLicit, String[] telescopeNames) {
    this.telescopeSize = telescopeSize;
    this.telescopeLicit = telescopeLicit;
    this.telescopeNames = telescopeNames;
  }

  @Override public int telescopeSize() { return telescopeSize; }
  @Override public boolean telescopeLicit(int i) { return telescopeLicit[i]; }
  @Override public @NotNull String telescopeName(int i) { return telescopeNames[i]; }

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
  public static class TeleSlice implements AbstractTelescope {
    private final AbstractTelescope signature;
    private final int size;
    public TeleSlice(@NotNull AbstractTelescope signature, int size) {
      this.signature = signature;
      this.size = size;
    }
    @Override public int telescopeSize() { return size; }
    @Override public boolean telescopeLicit(int i) { return signature.telescopeLicit(i); }
    @Override public @NotNull String telescopeName(int i) { return signature.telescopeName(i); }
    @Override public @NotNull Term telescope(int i, Seq<Term> teleArgs) {
      return signature.telescope(i, teleArgs);
    }
    @Override public @NotNull Term result(Seq<Term> teleArgs) {
      return ErrorTerm.DUMMY;
    }
  }
}

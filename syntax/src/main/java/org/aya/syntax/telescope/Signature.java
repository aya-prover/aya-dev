// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.telescope;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.PiTerm;
import org.aya.syntax.core.term.Term;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record Signature(@NotNull AbstractTele.Locns telescope, @NotNull ImmutableSeq<SourcePos> pos) {
  public Signature { assert telescope.telescopeSize() == pos.size(); }

  public @NotNull ImmutableSeq<Param> params() { return telescope.telescope(); }
  public @NotNull Term result() { return telescope.result(); }

  public @NotNull Signature descent(@NotNull UnaryOperator<Term> f) {
    return new Signature(new AbstractTele.Locns(telescope.telescope().map(x -> x.descent(f)), f.apply(telescope.result())), pos);
  }

  /**
   * The nicest thing about this is that if the whole thing is de Bruijn indexed,
   * the unpi-ed version of it will have the correct de Bruijn index.
   */
  public @NotNull Signature pusheen(UnaryOperator<Term> pre) {
    var resultPushed = PiTerm.unpiDBI(result(), pre);
    return new Signature(
      new AbstractTele.Locns(params().appendedAll(resultPushed.params().view()), resultPushed.body()),
      pos.appendedAll(ImmutableSeq.fill(resultPushed.params().size(), SourcePos.NONE)));
  }
}

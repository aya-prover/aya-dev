// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.telescope;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.DepTypeTerm;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.ForLSP;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/// Signature of a definition, used in concrete and tycking.
/// A Signature is supposed to be [Closed], that is, [#telescope] is [Closed].
///
/// @apiNote All terms in signature are as bound as possible.
@ForLSP
public record Signature(@Closed @NotNull AbstractTele.Locns telescope, @NotNull ImmutableSeq<SourcePos> pos) {
  public Signature { assert telescope.telescopeSize() == pos.size(); }

  public @NotNull ImmutableSeq<Param> params() { return telescope.telescope(); }
  public @NotNull Term result() { return telescope.result(); }
  public @Closed @NotNull Term result(SeqView<LocalVar> vars) {
    return telescope.result(vars.<Term>map(FreeTerm::new).toSeq());
  }

  public @NotNull Signature descent(@NotNull UnaryOperator<Term> f) {
    return new Signature(telescope.map((_, t) -> f.apply(t)), pos);
  }

  /// The nicest thing about this is that if the whole thing is de Bruijn indexed,
  /// the unpi-ed version of it will have the correct de Bruijn index.
  public @NotNull Signature pusheen(UnaryOperator<@Closed Term> pre) {
    var vars = params().mapTo(MutableList.create(), p -> new LocalVar(p.name()));
    // Always true, Signature is holds that property.
    @Closed Term instResult = result(vars.view());
    var resultPushed = DepTypeTerm.unpiAndBind(instResult, pre, vars);
    @Closed AbstractTele.Locns tele = new AbstractTele.Locns(
      params().appendedAll(resultPushed.params().view()),
      resultPushed.body()
    );

    return new Signature(tele,
      pos.appendedAll(ImmutableSeq.fill(resultPushed.params().size(), SourcePos.NONE)));
  }

  public @NotNull Signature bindTele(@NotNull LocalVar var, @NotNull Param type, @NotNull SourcePos newPos) {
    return new Signature(telescope.bind(var, type), pos.prepended(newPos));
  }
}

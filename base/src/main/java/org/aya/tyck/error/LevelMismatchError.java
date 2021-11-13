// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.unify.level.LevelEqnSet;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record LevelMismatchError(
  @Nullable SourcePos pos,
  @NotNull ImmutableSeq<LevelEqnSet.Eqn> eqns
) implements Problem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.vcat(Doc.english("Cannot solve the following level equation(s):"),
      Doc.nest(2, Doc.vcat(
        eqns.map(eqn -> eqn.toDoc(options)))));
  }

  @Override public @NotNull SourcePos sourcePos() {
    return pos != null ? pos : eqns.first().sourcePos();
  }

  @Override public @NotNull SeqLike<WithPos<Doc>> inlineHints(@NotNull DistillerOptions options) {
    return eqns.view().map(eqn ->
      new WithPos<>(eqn.sourcePos(), eqn.toDoc(options)));
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}

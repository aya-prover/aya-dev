// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.util.WithPos;
import org.aya.core.sort.LevelEqnSet;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqLike;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record LevelMismatchError(@Nullable SourcePos pos, @NotNull Seq<LevelEqnSet.Eqn> eqns) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.plain("Cannot solve some level equation(s)");
  }

  @Override public @NotNull SourcePos sourcePos() {
    return pos != null ? pos : eqns.first().sourcePos();
  }

  @Override public @NotNull SeqLike<WithPos<Doc>> inlineHints() {
    return eqns.view().map(eqn ->
      new WithPos<>(eqn.sourcePos(), eqn.toDoc()));
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}

// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.pretty.doc.Doc;
import org.aya.states.TyckState;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.xtt.DisjCofNF;
import org.aya.syntax.core.term.xtt.PartialTerm;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

public interface IllegalPartialElement extends TyckError, Stateful {

  record CofMismatch(@NotNull DisjCofNF cof1,
                     @NotNull DisjCofNF cof2,
                     @NotNull SourcePos sourcePos,
                     @NotNull TyckState state)
    implements IllegalPartialElement {

    @Override
    public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.english("Two cofibrations are not equivalent to each other."); // TODO: elaborate the info.
    }

  }

  record ValueMismatch(@NotNull PartialTerm.Clause cls1,
                       @NotNull PartialTerm.Clause cls2,
                       @NotNull SourcePos sourcePos,
                       @NotNull TyckState state)
    implements IllegalPartialElement {

    @Override
    public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.english("Two partial clauses are different in their intersection"); // TODO: elaborate the info.
    }
  }

  record BadPartialLHS(@NotNull Term lhs,
                       @NotNull SourcePos sourcePos,
                       @NotNull TyckState state)
    implements  IllegalPartialElement {
    @Override
    public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.english("LHS of this clause is not a cofibration.");
    }
  }
}

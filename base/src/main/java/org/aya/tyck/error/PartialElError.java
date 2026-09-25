// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.prettier.CorePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.states.TyckState;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.xtt.CofNF;
import org.aya.syntax.core.term.xtt.PartialTerm;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

public interface PartialElError extends TyckError, Stateful {
  record CofMismatch(
    @NotNull CofNF.OrVar<CofNF.Disj> cof1,
    @NotNull CofNF.OrVar<CofNF.Disj> cof2,
    @NotNull SourcePos sourcePos,
    @NotNull TyckState state
  ) implements PartialElError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.english("Two cofibrations are not equivalent to each other."); // TODO: elaborate the info.
    }
  }

  record ValueMismatch(
    @NotNull PartialTerm.Clause cls1,
    @NotNull PartialTerm.Clause cls2,
    @NotNull CofNF.OrVar<CofNF.Disj> intersect,
    @NotNull SourcePos sourcePos,
    @NotNull TyckState state
  ) implements PartialElError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("These partial clauses do not agree with each other:"),
        Doc.nest(2, cls1.tm().toDoc(options)),
        Doc.plain("and"),
        Doc.nest(2, cls2.tm().toDoc(options)),
        Doc.english("The intersection face is:"),
        Doc.nest(2, new CorePrettier(options).visitCofDisj(intersect))
      );
    }
  }

  record BadPartialLHS(
    @NotNull Term lhs,
    @NotNull SourcePos sourcePos,
    @NotNull TyckState state
  ) implements PartialElError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Expect a cofibration, got:"), lhs.toDoc(options));
    }
  }
}

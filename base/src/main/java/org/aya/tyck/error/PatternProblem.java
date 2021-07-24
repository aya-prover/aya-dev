// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.concrete.Pattern;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public sealed interface PatternProblem extends Problem {
  @NotNull Pattern pattern();

  @Override default @NotNull SourcePos sourcePos() {
    return pattern().sourcePos();
  }

  record PossiblePat(@NotNull Pattern pattern, @NotNull CallTerm.ConHead available) implements PatternProblem {
    @Override public @NotNull Doc describe() {
      return Doc.hsep(
        Doc.english("Absurd pattern does not fit here because"),
        Doc.styled(Style.code(), CoreDistiller.varDoc(available.ref())),
        Doc.english("is still available")
      );
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }

  record SplittingOnNonData(@NotNull Pattern pattern, @NotNull Term type) implements PatternProblem {
    @Override public @NotNull Doc describe() {
      return Doc.hsep(
        Doc.english("Cannot split on a non-inductive type"),
        Doc.styled(Style.code(), type.toDoc()),
        Doc.english("with a constructor pattern"),
        Doc.styled(Style.code(), pattern.toDoc()));
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }

  record UnavailableCtor(@NotNull Pattern pattern, @NotNull Severity level) implements PatternProblem {
    @Override public @NotNull Doc describe() {
      return Doc.hcat(
        Doc.english("Cannot match with"),
        Doc.ONE_WS,
        Doc.styled(Style.code(), pattern.toDoc()),
        Doc.ONE_WS,
        Doc.english("due to a failed index unification"),
        level == Severity.ERROR ? Doc.empty() : Doc.english(", treating as bind pattern")
      );
    }
  }

  record UnknownCtor(@NotNull Pattern pattern) implements PatternProblem {
    @Override public @NotNull Doc describe() {
      return Doc.hsep(
        Doc.english("Unknown constructor"),
        Doc.styled(Style.code(), pattern.toDoc())
      );
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }
}

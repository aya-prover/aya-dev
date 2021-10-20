// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.concrete.Pattern;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public sealed interface PatternProblem extends Problem {
  @NotNull Pattern pattern();

  @Override default @NotNull SourcePos sourcePos() {
    return pattern().sourcePos();
  }

  record PossiblePat(
    @Override @NotNull Pattern pattern,
    @NotNull CallTerm.ConHead available
  ) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("Absurd pattern does not fit here because"),
        Doc.styled(Style.code(), BaseDistiller.varDoc(available.ref())),
        Doc.english("is still available")
      );
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }

  record SplittingOnNonData(@Override @NotNull Pattern pattern, @NotNull Term type) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(
        Doc.english("Cannot split on a non-inductive type"),
        Doc.par(1, type.toDoc(options)),
        Doc.english("with a constructor pattern"),
        Doc.par(1, pattern.toDoc(options)));
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }

  record UnavailableCtor(@Override @NotNull Pattern pattern, @NotNull Severity level) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(
        Doc.english("Cannot match with"),
        Doc.par(1, pattern.toDoc(options)),
        Doc.cat(
          Doc.english("due to a failed index unification"),
          Doc.emptyIf(isError(), () -> Doc.english(", treating as bind pattern"))));
    }
  }

  record UnknownCtor(@Override @NotNull Pattern pattern) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(
        Doc.english("Unknown constructor"),
        Doc.par(1, pattern.toDoc(options))
      );
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }

  record TupleNonSig(@Override @NotNull Pattern.Tuple pattern, @NotNull Term type) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(
        Doc.english("The tuple pattern"),
        Doc.par(1, pattern.toDoc(options)),
        Doc.english("splits only on sigma types, while the actual type"),
        Doc.par(1, type.freezeHoles(null).toDoc(options)),
        Doc.english("does not look like one"));
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }

  record TooManyPattern(@Override @NotNull Pattern pattern, @NotNull Term retTy) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(
        Doc.english("There is no parameter for the pattern"),
        Doc.par(1, pattern.toDoc(options)),
        Doc.english("to match against, given the return type"),
        Doc.par(1, retTy.toDoc(options)),
        Doc.parened(Doc.sep(
          Doc.english("and in case it's a function type, you may want to move its parameters before the"),
          Doc.styled(Style.code(), ":"),
          Doc.english("in the signature"))));
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }
}

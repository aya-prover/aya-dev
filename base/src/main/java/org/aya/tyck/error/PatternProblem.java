// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import kala.tuple.Unit;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.concrete.Pattern;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Zonker;
import org.aya.distill.BaseDistiller;
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

  record SplittingOnNonData(@NotNull Pattern pattern, @NotNull Term type) implements PatternProblem {
    @Override public @NotNull Doc describe() {
      return Doc.sep(
        Doc.english("Cannot split on a non-inductive type"),
        Doc.styled(Style.code(), type.toDoc(DistillerOptions.DEFAULT)),
        Doc.english("with a constructor pattern"),
        Doc.styled(Style.code(), pattern.toDoc(DistillerOptions.DEFAULT)));
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }

  record UnavailableCtor(@NotNull Pattern pattern, @NotNull Severity level) implements PatternProblem {
    @Override public @NotNull Doc describe() {
      return Doc.cat(
        Doc.english("Cannot match with"),
        Doc.ONE_WS,
        Doc.styled(Style.code(), pattern.toDoc(DistillerOptions.DEFAULT)),
        Doc.ONE_WS,
        Doc.english("due to a failed index unification"),
        Doc.emptyIf(isError(), () -> Doc.english(", treating as bind pattern")));
    }
  }

  record UnknownCtor(@NotNull Pattern pattern) implements PatternProblem {
    @Override public @NotNull Doc describe() {
      return Doc.sep(
        Doc.english("Unknown constructor"),
        Doc.styled(Style.code(), pattern.toDoc(DistillerOptions.DEFAULT))
      );
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }

  record TupleNonSig(@NotNull Pattern.Tuple pattern, @NotNull Term type) implements PatternProblem {
    @Override public @NotNull Doc describe() {
      return Doc.sep(
        Doc.english("The tuple pattern"),
        Doc.styled(Style.code(), pattern.toDoc(DistillerOptions.DEFAULT)),
        Doc.english("splits only sigma types, while the actual type"),
        Doc.styled(Style.code(), type.freezeHoles(null).toDoc(DistillerOptions.DEFAULT)),
        Doc.english("does not look like one"));
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }

  record TooManyPattern(@NotNull Pattern pattern, @NotNull Term retTy) implements PatternProblem {
    @Override public @NotNull Doc describe() {
      return Doc.sep(
        Doc.english("There is no parameter for the pattern"),
        Doc.styled(Style.code(), pattern.toDoc(DistillerOptions.DEFAULT)),
        Doc.english("to match against (FYI the return type is"),
        Doc.styled(Style.code(), retTy.accept(Zonker.NO_REPORT, Unit.unit()).toDoc(DistillerOptions.DEFAULT)),
        Doc.english("and in case it's a function type, you may want to move its parameters before the"),
        Doc.styled(Style.code(), ":"),
        Doc.english("in the signature)"));
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }
}

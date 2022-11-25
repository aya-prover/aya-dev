// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Pattern;
import org.aya.core.pat.Pat;
import org.aya.core.term.ConCall;
import org.aya.core.term.DataCall;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public sealed interface PatternProblem extends Problem {
  @NotNull Pattern pattern();

  @Override default @NotNull SourcePos sourcePos() {
    return pattern().sourcePos();
  }

  record BlockedEval(@Override @NotNull Pattern pattern, @NotNull DataCall dataCall) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(
        Doc.english("Unsure if this pattern is actually impossible, as constructor selection is blocked on:"),
        Doc.par(1, dataCall.toDoc(options)));
    }
  }

  record PossiblePat(
    @Override @NotNull Pattern pattern,
    @NotNull ConCall.Head available
  ) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("Absurd pattern does not fit here because"),
        Doc.styled(Style.code(), BaseDistiller.varDoc(available.ref())),
        Doc.english("is still available")
      );
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
  }

  record UnavailableCtor(
    @Override @NotNull Pattern pattern,
    @NotNull DataCall dataCall
  ) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(
        Doc.english("I'm not sure if there should be a case for"),
        Doc.par(1, pattern.toDoc(options)),
        Doc.english("as index unification is blocked for type"),
        Doc.par(1, dataCall.toDoc(options)));
    }
  }

  record IllegalPropPat(
    @Override @NotNull Pattern pattern
  ) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(
        Doc.english("Prop pattern is disallowed in this context:"),
        Doc.par(1, pattern.toDoc(options)));
    }
  }

  record UnknownCtor(@Override @NotNull Pattern pattern) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(
        Doc.english("Unknown constructor"),
        Doc.par(1, pattern.toDoc(options))
      );
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
  }

  record CannotPush(
    @NotNull ImmutableSeq<Pat> results,
    @Override @NotNull Pattern pattern,
    @NotNull Term.Param param
  ) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(Doc.english("Cannot push this parameter:"),
        Doc.par(1, param.toDoc(options)),
        Doc.english("to the match result of this pattern:"),
        Doc.par(1, Doc.commaList(results.view().map(p -> p.toDoc(options)))),
        Doc.english("as no match result is provided.")
      );
    }
  }

  record TooManyImplicitPattern(@Override @NotNull Pattern pattern,
                                @NotNull Term.Param param) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(
        Doc.english("There are too many implicit patterns:"),
        Doc.par(1, pattern.toDoc(options)),
        Doc.english("should be an explicit pattern matched against"),
        Doc.par(1, param.toDoc(options)));
    }
  }


  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  record BadLitPattern(
    @NotNull Pattern pattern,
    @NotNull Term type
  ) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(Doc.english("The literal"),
        Doc.par(1, pattern.toDoc(options)),
        Doc.english("cannot be encoded as a term of type:"),
        Doc.par(1, type.toDoc(options)));
    }
  }
}

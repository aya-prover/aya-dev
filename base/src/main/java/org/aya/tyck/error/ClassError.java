// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourceNode;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;

public interface ClassError extends TyckError, SourceNodeProblem {
  @Override default @NotNull SourcePos sourcePos() { return expr().sourcePos(); }

  record NotFullyApplied(@Override @NotNull WithPos<Expr> expr) implements ClassError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Unable to new an incomplete class type:"), Doc.code(expr.data().toDoc(options)));
    }
  }

  record UnknownMember(@Override @NotNull SourcePos sourcePos, @NotNull String name) implements TyckError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Unknown member"),
        Doc.code(name), Doc.english("projected"));
    }
  }

  record InstanceNotFound(@Override @NotNull SourcePos sourcePos, @NotNull ClassCall clazz) implements TyckError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Instances for the class-call"),
        clazz.toDoc(options), Doc.english("not found"));
    }
  }

  record NotInstance(
    @Override @NotNull SourcePos sourcePos, @NotNull Term shouldBeClass,
    @NotNull Stateful state
  ) implements TyckError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var docs = MutableList.of(Doc.english("To be an instance, the type should be a class-call, but it isn't:"));
      UnifyInfo.exprInfo(shouldBeClass, options, state, docs);
      return Doc.vcat(docs);
    }
  }

  record InstanceAmbiguous(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ClassCall clazz, @NotNull ImmutableSeq<Term> instances
  ) implements TyckError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Non-unique instances for the class-call"),
        clazz.toDoc(options), Doc.english("found:"),
        Doc.commaList(instances.map(i -> i.toDoc(options))));
    }
  }

  record DifferentClass(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ClassCall want, @NotNull ClassCall got
  ) implements TyckError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Expected an instance of class"), BasePrettier.refVar(want.ref()),
        Doc.english("but got"), BasePrettier.refVar(got.ref()));
    }
  }

  record ProjIxError(@Override @NotNull SourceNode expr, int actual)
    implements TyckError, SourceNodeProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Index can only be 1 or 2, there's no"),
        Doc.ordinal(actual), Doc.english("projection"));
    }
  }
}

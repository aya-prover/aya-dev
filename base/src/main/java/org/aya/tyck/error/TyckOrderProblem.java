// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Signatured;
import org.aya.generic.util.InternalException;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.order.TyckUnit;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public interface TyckOrderProblem extends Problem {
  default @Override @NotNull Severity level() {
    return Severity.ERROR;
  }

  default @NotNull String nameOf(@NotNull TyckUnit stmt) {
    return switch (stmt) {
      case Decl decl -> decl.ref().name();
      case Remark remark -> "a remark";
      case Signatured signatured -> signatured.ref().name();
      default -> throw new InternalException("Unexpected stmt seen in SCCTycker: " + stmt);
    };
  }

  record CircularSignatureError(
    @NotNull ImmutableSeq<TyckUnit> cycles
  ) implements TyckOrderProblem {
    @Override public @NotNull SourcePos sourcePos() {
      return cycles.view().map(TyckUnit::sourcePos)
        .max(Comparator.comparingInt(SourcePos::endLine));
    }

    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("Circular signature dependency found between"),
        Doc.commaList(cycles.view().map(this::nameOf).toImmutableSeq()
          .sorted().view().map(Doc::plain))
      );
    }
  }

  record SelfReferenceError(
    @NotNull TyckUnit unit
  ) implements TyckOrderProblem {
    @Override public @NotNull SourcePos sourcePos() {
      return unit.sourcePos();
    }

    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.english("Self-reference found in the signature of"),
        Doc.plain(nameOf(unit)));
    }
  }
}

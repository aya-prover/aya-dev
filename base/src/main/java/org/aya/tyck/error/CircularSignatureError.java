// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Sample;
import org.aya.concrete.stmt.Stmt;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public record CircularSignatureError(
  @NotNull ImmutableSeq<Stmt> cycles
) implements Problem {
  @Override public @NotNull SourcePos sourcePos() {
    return cycles.view().map(Stmt::sourcePos)
      .max(Comparator.comparingInt(SourcePos::endLine));
  }

  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(
      Doc.english("Circular signature dependency found between"),
      Doc.commaList(cycles.view().map(this::nameOf).toImmutableSeq()
        .sorted().view().map(Doc::plain))
    );
  }

  private @NotNull String nameOf(@NotNull Stmt stmt) {
    return switch (stmt) {
      case Decl decl -> decl.ref().name();
      case Sample sample -> nameOf(sample.delegate());
      case Remark remark -> remark.raw;
      default -> throw new IllegalStateException("Unexpected stmt seen in SCCTycker: " + stmt);
    };
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}

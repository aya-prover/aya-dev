// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.stmt.TyckUnit;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.stmt.decl.Decl;
import org.aya.util.Panic;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public interface TyckOrderError extends TyckError {
  default @NotNull String nameOf(@NotNull TyckUnit stmt) {
    return switch (stmt) {
      case Decl decl -> decl.ref().name();
      default -> throw new Panic("Unexpected stmt seen in SCCTycker: " + stmt);
    };
  }

  record CircularSignature(@NotNull ImmutableSeq<TyckUnit> cycles) implements TyckOrderError {
    @Override public @NotNull SourcePos sourcePos() {
      return cycles.view().map(TyckUnit::sourcePos)
        .max(Comparator.comparingInt(SourcePos::tokenEndIndex));
    }

    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Circular signature dependency found between"),
        Doc.commaList(cycles.view().map(this::nameOf).toSeq()
          .sorted().view().map(Doc::plain))
      );
    }
  }

  record SelfReference(@NotNull TyckUnit expr) implements TyckOrderError, SourceNodeProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Self-reference found in the signature of"),
        Doc.plain(nameOf(expr)));
    }
  }
}

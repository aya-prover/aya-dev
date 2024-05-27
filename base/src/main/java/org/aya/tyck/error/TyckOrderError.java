// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.stmt.TyckUnit;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.stmt.decl.Decl;
import org.aya.syntax.ref.DefVar;
import org.aya.util.error.Panic;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.Contract;
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
        .max(Comparator.comparingInt(SourcePos::endLine));
    }

    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Circular signature dependency found between"),
        Doc.commaList(cycles.view().map(this::nameOf).toImmutableSeq()
          .sorted().view().map(Doc::plain))
      );
    }
  }

  record SelfReference(@NotNull TyckUnit unit) implements TyckOrderError {
    @Override public @NotNull SourcePos sourcePos() {
      return unit.sourcePos();
    }

    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Self-reference found in the signature of"),
        Doc.plain(nameOf(unit)));
    }
  }

  @Contract(pure = true)
  static @NotNull Panic notYetTycked(@NotNull DefVar<?, ?> var) {
    var msg = Doc.sep(Doc.english("Attempting to use a definition"),
      Doc.code(BasePrettier.refVar(var)),
      Doc.english("which is not yet typechecked"));
    return new Panic(msg.debugRender());
  }
}

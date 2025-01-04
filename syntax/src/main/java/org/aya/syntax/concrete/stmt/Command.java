// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.error.PosedUnaryOperator;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface Command extends Stmt {
  default @Override void descentInPlace(@NotNull PosedUnaryOperator<Expr> f, @NotNull PosedUnaryOperator<Pattern> p) { }
  /// @param sourcePosExceptLast can be NONE if the entire import is one `weakId`
  record Import(
    @NotNull SourcePos sourcePosExceptLast,
    @NotNull SourcePos sourcePosLast,
    @NotNull ModulePath path,
    @Nullable WithPos<String> asName,
    @Override @NotNull Accessibility accessibility
  ) implements Command {
    @Override public @NotNull SourcePos sourcePos() {
      if (sourcePosExceptLast == SourcePos.NONE) return sourcePosLast;
      return sourcePosExceptLast.union(sourcePosLast);
    }
  }

  /** @author re-xyr */
  record Open(
    @Override @NotNull SourcePos sourcePos,
    @Override @NotNull Accessibility accessibility,
    @NotNull ModuleName.Qualified path,
    @NotNull UseHide useHide,
    boolean openExample,
    boolean fromSugar
  ) implements Command { }

  /** @author re-xyr */
  record Module(
    @Override @NotNull SourcePos sourcePos,
    @NotNull SourcePos entireSourcePos,
    @NotNull String name,
    @NotNull ImmutableSeq<@NotNull Stmt> contents
  ) implements Command {
    @Override public @NotNull Accessibility accessibility() { return Accessibility.Public; }
    @Override public void descentInPlace(@NotNull PosedUnaryOperator<Expr> f, @NotNull PosedUnaryOperator<Pattern> p) {
      contents.forEach(stmt -> stmt.descentInPlace(f, p));
    }
  }
}

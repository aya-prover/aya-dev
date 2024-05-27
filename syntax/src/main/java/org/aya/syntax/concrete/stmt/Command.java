// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.error.PosedUnaryOperator;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface Command extends Stmt {
  default @Override void descentInPlace(@NotNull PosedUnaryOperator<Expr> f, @NotNull PosedUnaryOperator<Pattern> p) { }
  /** @author re-xyr */
  record Import(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ModulePath path,
    @Nullable String asName,
    @Override @NotNull Accessibility accessibility
  ) implements Command { }

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

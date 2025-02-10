// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Expr;
import org.aya.util.PrettierOptions;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;

public record MatchMissingReturnsError(@NotNull WithPos<Expr> expr) implements TyckError, SourceNodeProblem {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.sep(
      Doc.english("The match expression here is in a spot where the return type is required. Try adding"),
      Doc.code(Doc.styled(BasePrettier.KEYWORD, "returns")),
      Doc.english("followed by a type after the expression being matched.")
    );
  }
}

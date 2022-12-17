// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.concrete.stmt.TeleDecl;
import org.aya.pretty.doc.Doc;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public sealed interface PrimError extends TyckError {
  record NoResultType(@NotNull TeleDecl.PrimDecl prim) implements PrimError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(prim.toDoc(options),
        Doc.english("is expected to have a type"));
    }

    @Override public @NotNull SourcePos sourcePos() {
      return prim.sourcePos();
    }
  }

  record BadInterval(
    @NotNull SourcePos sourcePos,
    int integer
  ) implements TyckError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("The point"),
        Doc.code(String.valueOf(integer)),
        Doc.english("does not live in interval"));
    }

    @Override public @NotNull Doc hint(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Did you mean: "),
        Doc.code("0"),
        Doc.plain("or"),
        Doc.code("1")
      );
    }
  }
}

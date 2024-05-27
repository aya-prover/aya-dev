// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.def.PrimDef;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public interface PrimResolveError extends Problem {
  @Override default @NotNull Stage stage() { return Stage.RESOLVE; }
  @Override default @NotNull Severity level() { return Severity.ERROR; }

  record UnknownPrim(
    @Override @NotNull SourcePos sourcePos,
    @NotNull String name
  ) implements PrimResolveError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Unknown primitive"),
        Doc.code(name));
    }
  }

  record Redefinition(
    @NotNull String name,
    @Override @NotNull SourcePos sourcePos
  ) implements PrimResolveError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Redefinition of primitive"),
        Doc.code(name));
    }
  }

  /**
   * @author darkflames
   */
  record Dependency(
    @NotNull String name,
    @NotNull ImmutableSeq<PrimDef.ID> lack,
    @Override @NotNull SourcePos sourcePos
  ) implements PrimResolveError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      assert lack.isNotEmpty();
      return Doc.sep(
        Doc.english("The primitive"), Doc.code(name),
        Doc.english("depends on undeclared primitive(s):"),
        Doc.commaList(lack.map(name -> Doc.code(name.id))));
    }
  }
}

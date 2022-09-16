// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.PrimDef;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public interface PrimResolveError extends Problem {
  @Override default @NotNull Stage stage() {return Stage.RESOLVE;}
  @Override default @NotNull Severity level() {return Severity.ERROR;}

  record UnknownPrim(
    @Override @NotNull SourcePos sourcePos,
    @NotNull String name
  ) implements PrimResolveError {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("Unknown primitive"),
        Doc.styled(Style.code(), Doc.plain(name)));
    }
  }

  record Redefinition(
    @NotNull String name,
    @Override @NotNull SourcePos sourcePos
  ) implements PrimResolveError {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.english("Redefinition of primitive"),
        Doc.styled(Style.code(), Doc.plain(name)));
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
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      assert lack.isNotEmpty();
      return Doc.sep(
        Doc.english("The prim"), Doc.styled(Style.code(), Doc.plain(name)),
        Doc.english("depends on undeclared prims:"),
        Doc.commaList(lack.map(name -> Doc.styled(Style.code(), Doc.plain(name.id)))));
    }
  }
}

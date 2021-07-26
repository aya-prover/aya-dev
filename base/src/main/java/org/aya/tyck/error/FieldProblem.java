// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.core.visitor.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public interface FieldProblem extends Problem {
  record MissingFieldError(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Var> missing
  ) implements FieldProblem {
    @Override public @NotNull Doc describe() {
      return Doc.sep(Doc.english("Missing field(s):"), Doc.join(Doc.plain(", "), missing.view()
        .map(CoreDistiller::varDoc)
        .map(m -> Doc.styled(Style.code(), m))));
    }
  }

  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }
  record NoSuchFieldError(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<String> notFound
  ) implements FieldProblem {
    @Override public @NotNull Doc describe() {
      return Doc.sep(Doc.plain("No such field(s):"),
        Doc.join(Doc.plain(", "), notFound.view()
          .map(m -> Doc.styled(Style.code(), Doc.plain(m))))
      );
    }
  }
}

// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.FieldDef;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public sealed interface FieldError extends TyckError {
  record MissingField(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<AnyVar> missing
  ) implements FieldError {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.english("Missing field(s):"), Doc.commaList(missing.view()
        .map(BaseDistiller::varDoc)
        .map(m -> Doc.code(m))));
    }
  }
  record NoSuchField(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<String> notFound
  ) implements FieldError {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.english("No such field(s):"),
        Doc.commaList(notFound.view()
          .map(m -> Doc.code(Doc.plain(m))))
      );
    }
  }

  record UnknownField(
    @Override @NotNull SourcePos sourcePos,
    @NotNull String name
  ) implements FieldError {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("Unknown field"),
        Doc.code(Doc.plain(name)),
        Doc.english("projected")
      );
    }
  }

  record ArgMismatch(
    @Override @NotNull SourcePos sourcePos,
    @NotNull FieldDef fieldDef,
    int supplied
  ) implements FieldError {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.english("Expected"),
        Doc.plain(String.valueOf(fieldDef.ref.core.selfTele.size())),
        Doc.english("arguments, but found"),
        Doc.plain(String.valueOf(supplied)),
        Doc.english("arguments for field"),
        BaseDistiller.linkRef(fieldDef.ref, BaseDistiller.FIELD_CALL));
    }
  }
}

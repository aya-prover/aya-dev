// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.CommonDecl;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.FnDef;
import org.aya.core.def.UserDef;
import org.aya.core.term.DataCall;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.generic.AyaDocile;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

import static org.aya.pretty.doc.Doc.plain;

public record PositivityError(
  @Override @NotNull SourcePos sourcePos,
  @NotNull AyaDocile what,
  @NotNull AyaDocile result
) implements TyckError {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.sep(
      Doc.english("Positivity check failed for"),
      what.toDoc(options),
      Doc.english("->"),
      result.toDoc(options));
  }
}

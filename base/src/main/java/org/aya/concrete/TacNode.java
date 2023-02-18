// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

sealed public interface TacNode extends AyaDocile {
  record ExprTac(@NotNull SourcePos sourcePos, @NotNull Expr expr) implements TacNode {
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.symbol("|"), expr.toDoc(options));
    }
  }

  record ListExprTac(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<TacNode> tacNodes) implements TacNode {
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Doc.cblock(Doc.empty(), 2, Doc.emptyIf(tacNodes.isEmpty(), () ->
        Doc.vcat(tacNodes.view().map(tacNode -> tacNode.toDoc(options)))));
    }
  }
}

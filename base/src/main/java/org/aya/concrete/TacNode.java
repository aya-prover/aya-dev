// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import org.aya.pretty.doc.Doc;
import org.aya.util.distill.AyaDocile;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

sealed public interface TacNode extends AyaDocile {

  @NotNull SourcePos sourcePos();

  record ExprTac(@Override @NotNull SourcePos sourcePos, @NotNull Expr expr) implements TacNode {
    @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.symbol("|"), expr.toDoc(options));
    }
  }

  record ListExprTac(@Override @NotNull SourcePos sourcePos, @NotNull ImmutableSeq<TacNode> tacNodes) implements TacNode {
    @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return Doc.cblock(Doc.empty(), 2, Doc.emptyIf(tacNodes.isEmpty(), () ->
        Doc.vcat(tacNodes.view().map(tacNode -> tacNode.toDoc(options)))));
    }
  }
}

// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.core.pat.Lhs;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.distill.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

/** @author ice1000 */
public record Matching(
  @NotNull SourcePos sourcePos,
  @NotNull ImmutableSeq<Lhs> lhss,
  @NotNull Term body
) implements AyaDocile {
  @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    var distiller = new CoreDistiller(options);
    var lhsss = options.map.get(DistillerOptions.Key.ShowImplicitPats) ? lhss : lhss.view().filter(Lhs::explicit);
    var doc = Doc.emptyIf(lhsss.isEmpty(), () -> Doc.cat(Doc.ONE_WS, Doc.commaList(
      lhsss.view().map(l -> distiller.visitLhs(l, BaseDistiller.Outer.Free)))));
    return Doc.sep(doc, Doc.symbol("=>"), body.toDoc(options));
  }
}

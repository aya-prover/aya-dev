// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.SourcePos;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.distill.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

/** @author ice1000 */
public record Matching(
  @NotNull SourcePos sourcePos,
  @NotNull ImmutableSeq<Pat> patterns,
  @NotNull Term body
) implements AyaDocile {
  @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    var doc = new CoreDistiller(options).visitMaybeCtorPatterns(
      patterns, BaseDistiller.Outer.Free, Doc.cat(Doc.plain(","), Doc.ONE_WS));
    return Doc.sep(doc, Doc.symbol("=>"), body().toDoc(options));
  }
}

// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.SourcePos;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.distill.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.NotNull;

/** @author ice1000 */
public record Matching(
  @NotNull SourcePos sourcePos,
  @NotNull ImmutableSeq<Pat> patterns,
  @NotNull Term body
) implements Docile {
  @Override public @NotNull Doc toDoc() {
    var doc = CoreDistiller.INSTANCE.visitMaybeCtorPatterns(this.patterns(), false, Doc.plain(", "));
    return Doc.sep(doc, Doc.symbol("=>"), this.body().toDoc());
  }
}

// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.aya.util.distill.AyaDocile;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

/** @author ice1000 */
public record Matching(
  @NotNull SourcePos sourcePos,
  @NotNull ImmutableSeq<Pat> patterns,
  @NotNull Term body
) implements AyaDocile {
  @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return Pat.Preclause.weaken(this).toDoc(options);
  }
}

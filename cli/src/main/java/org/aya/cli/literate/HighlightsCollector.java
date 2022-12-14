// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.remark.Literate;
import org.aya.concrete.remark.LiterateConsumer;
import org.jetbrains.annotations.NotNull;

public record HighlightsCollector(@NotNull ImmutableSeq<HighlightInfo> highlights) implements LiterateConsumer {
  @Override public void accept(@NotNull Literate literate) {
    if (literate instanceof Literate.CodeBlock codeBlock && codeBlock.isAya() && codeBlock.sourcePos != null) {
      var hl = highlights.filter(x -> codeBlock.sourcePos.containsIndex(x.sourcePos()));
      codeBlock.highlighted = FaithfulDistiller.highlight(codeBlock.raw, codeBlock.sourcePos.tokenStartIndex(), hl);
    }
    LiterateConsumer.super.accept(literate);
  }
}

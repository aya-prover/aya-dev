// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.remark.Literate;
import org.aya.concrete.remark.LiterateConsumer;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public class HighlightsCollector implements LiterateConsumer {
  public final @NotNull ImmutableSeq<HighlightInfo> highlights;
  private final @NotNull FaithfulPrettier prettier;

  public HighlightsCollector(@NotNull ImmutableSeq<HighlightInfo> highlights, @NotNull PrettierOptions options) {
    this.highlights = highlights;
    this.prettier = new FaithfulPrettier(options);
  }

  @Override public void accept(@NotNull Literate literate) {
    if (literate instanceof Literate.CodeBlock codeBlock && codeBlock.isAya() && codeBlock.sourcePos != null) {
      var hl = highlights.filter(x -> codeBlock.sourcePos.containsIndex(x.sourcePos()));
      codeBlock.highlighted = prettier.highlight(codeBlock.raw, codeBlock.sourcePos.tokenStartIndex(), hl);
    }
    LiterateConsumer.super.accept(literate);
  }
}

// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.concrete.remark.Literate;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public interface LiterateConsumer extends Consumer<Literate> {
  @MustBeInvokedByOverriders
  default void accept(@NotNull Literate literate) {
    switch (literate) {
      case Literate.Many many -> many.children().forEach(this);
      case Literate.Unsupported(var children) -> children.forEach(this);
      case Literate misc -> {}
    }
  }

  record AyaCodeBlocks(@NotNull MutableList<Literate.CodeBlock> codeBlocks) implements LiterateConsumer {
    public static @NotNull ImmutableSeq<Literate.CodeBlock> codeBlocks(@NotNull Literate literate) {
      var consumer = new AyaCodeBlocks(MutableList.create());
      consumer.accept(literate);
      return consumer.codeBlocks().toImmutableSeq();
    }

    @Override public void accept(@NotNull Literate literate) {
      if (literate instanceof Literate.CodeBlock codeBlock && codeBlock.isAya()) {
        codeBlocks.append(codeBlock);
      }
      LiterateConsumer.super.accept(literate);
    }
  }

  record Codes(@NotNull MutableList<Literate.Code> codes) implements LiterateConsumer {
    @Override public void accept(@NotNull Literate literate) {
      if (literate instanceof Literate.Code code) {
        codes.append(code);
      }
      LiterateConsumer.super.accept(literate);
    }
  }

  /**
   * @param highlights natural ordered
   */
  record Highlight(@NotNull ImmutableSeq<HighlightInfo> highlights) implements LiterateConsumer {
    @Override public void accept(@NotNull Literate literate) {
      if (literate instanceof Literate.CodeBlock codeBlock && codeBlock.isAya() && codeBlock.sourcePos != null) {
        var hl = highlights.filter(x -> codeBlock.sourcePos.containsIndex(x.sourcePos()));
        codeBlock.highlighted = FaithfulDistiller.highlight(codeBlock.raw, codeBlock.sourcePos.tokenStartIndex(), hl);
      }
      LiterateConsumer.super.accept(literate);
    }
  }
}

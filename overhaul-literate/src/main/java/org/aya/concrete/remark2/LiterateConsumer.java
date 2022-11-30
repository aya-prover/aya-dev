// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark2;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.literate.HighlightInfo;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public interface LiterateConsumer extends Consumer<Literate> {
  @MustBeInvokedByOverriders
  default void accept(@NotNull Literate literate) {
    switch (literate) {
      case Literate.Code code -> {}
      case Literate.CodeBlock codeBlock -> {}
      case Literate.Err err -> {}
      case Literate.Many many -> many.children().forEach(this);
      case Literate.Raw raw -> {}
      case Literate.Unsupported(var children) -> children.forEach(this);
    }
  }

  class AyaCodeBlocks implements LiterateConsumer {
    public static @NotNull ImmutableSeq<Literate.CodeBlock> codeBlocks(@NotNull Literate literate) {
      var consumer = new AyaCodeBlocks();
      consumer.accept(literate);
      return consumer.codeBlocks();
    }

    private final MutableList<Literate.CodeBlock> codeBlocks = MutableList.create();

    @Override
    public void accept(@NotNull Literate literate) {
      if (literate instanceof Literate.CodeBlock codeBlock && codeBlock.isAya()) {
        codeBlocks.append(codeBlock);
      }

      LiterateConsumer.super.accept(literate);
    }

    public @NotNull ImmutableSeq<Literate.CodeBlock> codeBlocks() {
      return codeBlocks.toImmutableSeq();
    }
  }

  class Codes implements LiterateConsumer {
    private final @NotNull MutableList<Literate.Code> codes = MutableList.create();

    @Override
    public void accept(@NotNull Literate literate) {
      if (literate instanceof Literate.Code code) {
        codes.append(code);
      }

      LiterateConsumer.super.accept(literate);
    }

    public @NotNull SeqView<Literate.Code> codes() {
      return codes.view();
    }
  }

  /**
   * @param highlights natural ordered
   */
  record Highlight(@NotNull ImmutableSeq<HighlightInfo> highlights) implements LiterateConsumer {
    @Override public void accept(@NotNull Literate literate) {
      if (literate instanceof Literate.CodeBlock codeBlock) {
        if (codeBlock.isAya() && codeBlock.sourcePos instanceof SourcePos sourcePos) {
          var highlights = this.highlights.view().filter(x -> sourcePos.containsIndex(x.sourcePos()));
          codeBlock.highlighted = FaithfulDistiller.highlight(codeBlock.raw, sourcePos.tokenStartIndex(), highlights);
        }
      }

      LiterateConsumer.super.accept(literate);
    }

  }
}

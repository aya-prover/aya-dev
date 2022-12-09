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

  interface LiterateExtractinator<T> extends LiterateConsumer {
    @NotNull MutableList<T> result();

    default @NotNull ImmutableSeq<T> extract(Literate literate) {
      accept(literate);
      return result().toImmutableSeq();
    }
  }


  record AyaCodeBlocks(@NotNull MutableList<Literate.CodeBlock> result)
    implements LiterateExtractinator<Literate.CodeBlock> {
    @Override public void accept(@NotNull Literate literate) {
      if (literate instanceof Literate.CodeBlock codeBlock && codeBlock.isAya()) {
        result.append(codeBlock);
      }
      LiterateExtractinator.super.accept(literate);
    }
  }

  record Codes(@NotNull MutableList<Literate.Code> result)
    implements LiterateExtractinator<Literate.Code> {
    @Override public void accept(@NotNull Literate literate) {
      if (literate instanceof Literate.Code code) {
        result.append(code);
      }
      LiterateExtractinator.super.accept(literate);
    }
  }

  record Highlights(@NotNull ImmutableSeq<HighlightInfo> highlights) implements LiterateConsumer {
    @Override public void accept(@NotNull Literate literate) {
      if (literate instanceof Literate.CodeBlock codeBlock && codeBlock.isAya() && codeBlock.sourcePos != null) {
        var hl = highlights.filter(x -> codeBlock.sourcePos.containsIndex(x.sourcePos()));
        codeBlock.highlighted = FaithfulDistiller.highlight(codeBlock.raw, codeBlock.sourcePos.tokenStartIndex(), hl);
      }
      LiterateConsumer.super.accept(literate);
    }
  }
}

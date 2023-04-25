// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate;

import kala.collection.immutable.ImmutableSeq;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.pretty.doc.Link;
import org.aya.pretty.doc.Style;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author hoshino
 * @see LiterateConsumer
 */
public interface Literate extends Docile {
  record Raw(@NotNull Doc toDoc) implements Literate {
  }

  record List(@NotNull ImmutableSeq<Literate> items, boolean ordered) implements Literate {
    @Override public @NotNull Doc toDoc() {
      return Doc.list(ordered, items.map(Literate::toDoc));
    }
  }

  record HyperLink(@NotNull String href, @Nullable String hover,
                   @NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override public @NotNull Doc toDoc() {
      var child = Doc.cat(this.children().map(Literate::toDoc));
      return Doc.hyperLink(child, Link.page(href), hover);
    }
  }

  record Image(@NotNull String src, @NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override public @NotNull Doc toDoc() {
      var child = Doc.cat(this.children().map(Literate::toDoc));
      return Doc.image(child, Link.page(src));
    }
  }

  record Math(boolean inline, @NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override public @NotNull Doc toDoc() {
      var child = Doc.cat(this.children().map(Literate::toDoc));
      return inline ? Doc.math(child) : Doc.mathBlock(child);
    }
  }

  record Many(@Nullable Style style, @NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override public @NotNull Doc toDoc() {
      var child = Doc.cat(this.children().map(Literate::toDoc));
      return style == null ? child : Doc.styled(style, child);
    }
  }

  class CommonCode implements Literate {
    public final @NotNull String code;
    public final @NotNull SourcePos sourcePos;

    public CommonCode(@NotNull String code, @NotNull SourcePos sourcePos) {
      this.code = code;
      this.sourcePos = sourcePos;
    }

    /**
     * The content of this inline code
     */
    public @NotNull String code() {
      return code;
    }

    /**
     * The source pos of this inline code
     */
    public @NotNull SourcePos sourcePos() {
      return sourcePos;
    }

    @Override
    public @NotNull Doc toDoc() {
      return Doc.code(code);
    }
  }

  interface AnyCodeBlock extends Literate {
    /**
     * The language of this code block
     */
    @Contract(pure = true)
    @NotNull String language();

    /**
     * The code of this code block
     */
    @Contract(pure = true)
    @NotNull String code();

    /**
     * The source pos of this code block, without '```\n' and '\n```'
     *
     * @return null if the code block is empty, because the empty source pos doesn't exist.
     */
    @Contract(pure = true)
    @Nullable SourcePos sourcePos();
  }

  record UnknownCodeBlock(
    @Override @NotNull String language,
    @Override @NotNull String code,
    @Override @Nullable SourcePos sourcePos
  ) implements AnyCodeBlock {
    @Override
    public @NotNull Doc toDoc() {
      return Doc.plain(code);
    }
  }

  record Unsupported(@NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override
    public @NotNull Doc toDoc() {
      return Doc.vcat(children.map(Literate::toDoc));
    }
  }
}

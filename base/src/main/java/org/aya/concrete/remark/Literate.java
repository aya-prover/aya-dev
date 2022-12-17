// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark;

import kala.collection.immutable.ImmutableSeq;
import kala.value.MutableValue;
import org.aya.concrete.Expr;
import org.aya.core.def.UserDef;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.pretty.doc.Link;
import org.aya.pretty.doc.Style;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.tyck.ExprTycker;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author hoshino
 * @see LiterateConsumer
 */
public sealed interface Literate extends Docile {
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

  record Many(@Nullable Style style, @NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override public @NotNull Doc toDoc() {
      var child = Doc.cat(this.children().map(Literate::toDoc));
      return style == null ? child : Doc.styled(style, child);
    }
  }

  record Err(@NotNull MutableValue<AnyVar> def, @Override @NotNull SourcePos sourcePos) implements Literate {
    @Override public @NotNull Doc toDoc() {
      if (def.get() instanceof DefVar<?, ?> defVar && defVar.core instanceof UserDef<?> userDef) {
        var problems = userDef.problems;
        if (problems == null) return Doc.styled(Style.bold(), Doc.english("No error message."));
        return Doc.vcat(problems.map(problem -> problem.brief(AyaPrettierOptions.informative())));
      }
      return Doc.styled(Style.bold(), Doc.english("Not a definition that can obtain error message."));
    }
  }

  final class Code implements Literate {
    public final @NotNull String code;
    public final @NotNull SourcePos sourcePos;
    public @Nullable Expr expr;
    public @Nullable ExprTycker.Result tyckResult;
    public final @NotNull CodeOptions options;

    public Code(@NotNull String code, @NotNull SourcePos sourcePos, @NotNull CodeOptions options) {
      this.code = code;
      this.sourcePos = sourcePos;
      this.options = options;
    }

    @Override public @NotNull Doc toDoc() {
      if (tyckResult == null) {
        if (expr != null) return Doc.code(expr.toDoc(options.options()));
        else return Doc.code("Error");
      }
      assert expr != null;
      return Doc.code((switch (options.showCode()) {
        case Concrete -> expr;
        case Core -> tyckResult.wellTyped();
        case Type -> tyckResult.type();
      }).toDoc(options.options()));
    }
  }

  /**
   * A code block
   *
   * @implNote the content which this code block hold can be illegal, like
   * <pre>
   * ```aya<br/>
   * def foo : Nat =>
   * ```<br/>
   * </pre>
   */
  final class CodeBlock implements Literate {
    public final @NotNull String language;
    public final @NotNull String raw;
    public @Nullable Doc highlighted;

    /**
     * The source pos of the code block ( without beginning '```', and ending '\n```' )
     * null if this code block is empty (length 0)
     */
    public final @Nullable SourcePos sourcePos;

    public CodeBlock(@Nullable SourcePos sourcePos, @NotNull String language, @NotNull String raw) {
      this.language = language;
      this.raw = raw;
      this.sourcePos = sourcePos;
    }

    public boolean isAya() {
      return language.equalsIgnoreCase("aya");
    }

    @Override
    public @NotNull Doc toDoc() {
      var doc = isAya() && highlighted != null ? highlighted : Doc.plain(raw);
      return Doc.codeBlock(language, doc);
    }
  }

  record Unsupported(@NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override
    public @NotNull Doc toDoc() {
      return Doc.vcat(children.map(Literate::toDoc));
    }
  }
}

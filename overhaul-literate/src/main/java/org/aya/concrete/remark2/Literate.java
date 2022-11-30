// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark2;

import kala.collection.immutable.ImmutableSeq;
import kala.value.MutableValue;
import org.aya.concrete.Expr;
import org.aya.core.def.UserDef;
import org.aya.core.term.Term;
import org.aya.generic.util.InternalException;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.pretty.doc.Style;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public sealed interface Literate extends Docile {
  record Raw(@NotNull Doc toDoc) implements Literate {
  }

  record Many(@Nullable Style style, @NotNull ImmutableSeq<Literate> children, boolean isBlock) implements Literate {
    @Override public @NotNull Doc toDoc() {
      var docs = children().map(Literate::toDoc);
      var cat = isBlock ? Doc.vcat(docs) : Doc.cat(docs);
      return style == null ? cat : Doc.styled(style, cat);
    }
  }

  record Err(@NotNull MutableValue<AnyVar> def, @Override @NotNull SourcePos sourcePos) implements Literate {
    @Override public @NotNull Doc toDoc() {
      if (def.get() instanceof DefVar<?, ?> defVar && defVar.core instanceof UserDef<?> userDef) {
        var problems = userDef.problems;
        if (problems == null) return Doc.styled(Style.bold(), Doc.english("No error message."));
        return Doc.vcat(problems.map(problem -> problem.brief(DistillerOptions.informative())));
      }
      return Doc.styled(Style.bold(), Doc.english("Not a definition that can obtain error message."));
    }
  }

  final class ErrorLiterate implements Literate {
    public static final ErrorLiterate INSTANCE = new ErrorLiterate();

    private ErrorLiterate() {
    }

    @Override
    public @NotNull Doc toDoc() {
      // TODO
      throw new UnsupportedOperationException("TODO");
    }
  }

  // TODO
  final class Code implements Literate {
    public @NotNull Expr expr;
    public @Nullable ExprTycker.Result tyckResult;
    public @Nullable TyckState state;
    public final @NotNull CodeOptions options;

    public Code(@NotNull Expr expr, @NotNull CodeOptions options) {
      this.expr = expr;
      this.options = options;
    }

    private @NotNull Doc normalize(@NotNull Term term) {
      var mode = options.mode();
      if (state == null) throw new InternalException("No tyck state");
      return term.normalize(state, mode).toDoc(options.options());
    }

    @Override public @NotNull Doc toDoc() {
      if (tyckResult == null) return Doc.plain("Error");
      return Doc.styled(Style.code(), switch (options.showCode()) {
        case Concrete -> expr.toDoc(options.options());
        case Core -> normalize(tyckResult.wellTyped());
        case Type -> normalize(tyckResult.type());
      });
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
      if (isAya() && highlighted != null) {
        return highlighted;
      }

      return Doc.plain(raw);
    }
  }

  record Unsupported(@NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override
    public @NotNull Doc toDoc() {
      return Doc.vcat(children.map(Literate::toDoc));
    }
  }
}

// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.remark;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.value.Ref;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.visitor.ExprResolver;
import org.aya.concrete.visitor.ExprFixpoint;
import org.aya.core.def.UserDef;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public sealed interface Literate {
  default void resolve(@NotNull BinOpSet opSet, @NotNull Context context) {
  }

  @NotNull Doc toDoc();

  record Raw(@NotNull Doc toDoc) implements Literate {
  }

  record Styled(@NotNull Style style, @NotNull ImmutableSeq<Literate> content) implements Literate {
    @Override public @NotNull Doc toDoc() {
      return Doc.styled(style, Doc.cat(content.map(Literate::toDoc)));
    }
  }

  record Err(@NotNull Ref<Var> def, @NotNull SourcePos sourcePos) implements Literate {
    @Override public void resolve(@NotNull BinOpSet opSet, @NotNull Context context) {
      def.set(context.getUnqualified(def.value.name(), sourcePos));
    }

    @Override public @NotNull Doc toDoc() {
      if (def.value instanceof DefVar<?, ?> defVar && defVar.core instanceof UserDef userDef) {
        var problems = userDef.problems;
        if (problems == null) return Doc.styled(Style.bold(), Doc.english("No error message."));
        return Doc.vcat(problems.map(Problem::brief));
      }
      return Doc.styled(Style.bold(), Doc.english("Not a definition that can obtain error message."));
    }
  }

  /**
   * @param isType <code>true</code> if show type, otherwise show the term itself
   * @param mode   <code>null</code> if as-is
   */
  record CodeCmd(boolean isType, @Nullable NormalizeMode mode) {
  }

  record Code(@NotNull Ref<Expr> expr, @NotNull CodeCmd cmd) implements Literate {
    @Contract(mutates = "this")
    public <P> void modify(@NotNull ExprFixpoint<P> fixpoint, P p) {
      expr.set(expr.value.accept(fixpoint, p));
    }

    @Override public void resolve(@NotNull BinOpSet opSet, @NotNull Context context) {
      modify(new ExprResolver(false, Buffer.create()), context);
    }

    @Override public @NotNull Doc toDoc() {
      // TODO: need term
      return expr.value.toDoc();
    }
  }

  record Par(@NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override public void resolve(@NotNull BinOpSet opSet, @NotNull Context context) {
      children.forEach(child -> child.resolve(opSet, context));
    }

    @Override public @NotNull Doc toDoc() {
      return Doc.cat(children.map(Literate::toDoc));
    }
  }
}

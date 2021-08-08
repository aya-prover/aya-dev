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
import org.aya.concrete.visitor.ExprConsumer;
import org.aya.concrete.visitor.ExprFixpoint;
import org.aya.core.def.UserDef;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.tyck.ExprTycker;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public sealed interface Literate {
  default <P> void modify(@NotNull ExprFixpoint<P> fixpoint, P p) {
  }
  default <P> void visit(@NotNull ExprConsumer<P> consumer, P p) {
  }
  default void tyck(@NotNull ExprTycker tycker) {
  }

  void resolve(@NotNull BinOpSet opSet, @NotNull Context context);
  @NotNull Doc toDoc();

  record Raw(@NotNull Doc toDoc) implements Literate {
    @Override public void resolve(@NotNull BinOpSet opSet, @NotNull Context context) {
    }
  }

  record Many(@Nullable Style style, @NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override public void resolve(@NotNull BinOpSet opSet, @NotNull Context context) {
      children.forEach(child -> child.resolve(opSet, context));
    }

    @Override public <P> void modify(@NotNull ExprFixpoint<P> fixpoint, P p) {
      children.forEach(literate -> literate.modify(fixpoint, p));
    }

    @Override public <P> void visit(@NotNull ExprConsumer<P> consumer, P p) {
      children.forEach(literate -> literate.visit(consumer, p));
    }

    @Override public void tyck(@NotNull ExprTycker tycker) {
      children.forEach(literate -> literate.tyck(tycker));
    }

    @Override public @NotNull Doc toDoc() {
      var cat = Doc.cat(children.map(Literate::toDoc));
      return style == null ? cat : Doc.styled(style, cat);
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

  enum ShowCode {
    Concrete, Core, Type
  }

  final class Code implements Literate {
    public @NotNull Expr expr;
    public @Nullable ExprTycker.Result tyckResult;
    public final @NotNull ShowCode showCode;
    public final @Nullable NormalizeMode mode;

    /**
     * @param mode <code>null</code> if as-is
     */
    public Code(@NotNull Expr expr, @NotNull ShowCode showCode, @Nullable NormalizeMode mode) {
      this.expr = expr;
      this.showCode = showCode;
      this.mode = mode;
    }

    @Override @Contract(mutates = "this")
    public <P> void modify(@NotNull ExprFixpoint<P> fixpoint, P p) {
      expr = expr.accept(fixpoint, p);
    }

    @Override public <P> void visit(@NotNull ExprConsumer<P> consumer, P p) {
      expr.accept(consumer, p);
    }

    @Override public void tyck(@NotNull ExprTycker tycker) {
      tyckResult = tycker.checkExpr(expr, null);
    }

    @Override public void resolve(@NotNull BinOpSet opSet, @NotNull Context context) {
      modify(new ExprResolver(false, Buffer.create()), context);
    }

    @Override public @NotNull Doc toDoc() {
      // TODO: need term
      return expr.toDoc();
    }
  }
}

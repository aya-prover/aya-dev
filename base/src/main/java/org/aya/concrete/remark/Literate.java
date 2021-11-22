// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark;

import kala.collection.immutable.ImmutableSeq;
import kala.value.Ref;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.concrete.Expr;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.visitor.ExprResolver;
import org.aya.concrete.visitor.ExprConsumer;
import org.aya.concrete.visitor.ExprFixpoint;
import org.aya.core.def.UserDef;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.pretty.doc.Style;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.order.TyckUnit;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public sealed interface Literate extends Docile {
  default <P> void modify(@NotNull ExprFixpoint<P> fixpoint, P p) {
  }
  default <P> void visit(@NotNull ExprConsumer<P> consumer, P p) {
  }
  default void tyck(@NotNull ExprTycker tycker) {
  }

  @NotNull ImmutableSeq<TyckUnit> resolve(@NotNull ResolveInfo info, @NotNull Context context);

  record Raw(@NotNull Doc toDoc) implements Literate {
    @Override public @NotNull ImmutableSeq<TyckUnit> resolve(@NotNull ResolveInfo info, @NotNull Context context) {
      return ImmutableSeq.empty();
    }
  }

  record Many(@Nullable Style style, @NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override public @NotNull ImmutableSeq<TyckUnit> resolve(@NotNull ResolveInfo info, @NotNull Context context) {
      children.forEach(child -> child.resolve(info, context));
      return ImmutableSeq.empty();
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

  record Err(@NotNull Ref<Var> def, @Override @NotNull SourcePos sourcePos) implements Literate {
    @Override public @NotNull ImmutableSeq<TyckUnit> resolve(@NotNull ResolveInfo info, @NotNull Context context) {
      def.set(context.getUnqualified(def.value.name(), sourcePos));
      // TODO: add to dependency?
      return ImmutableSeq.empty();
    }

    @Override public @NotNull Doc toDoc() {
      if (def.value instanceof DefVar<?, ?> defVar && defVar.core instanceof UserDef userDef) {
        var problems = userDef.problems;
        if (problems == null) return Doc.styled(Style.bold(), Doc.english("No error message."));
        return Doc.vcat(problems.map(problem -> problem.brief(DistillerOptions.informative())));
      }
      return Doc.styled(Style.bold(), Doc.english("Not a definition that can obtain error message."));
    }
  }

  final class Code implements Literate {
    public @NotNull Expr expr;
    public @Nullable ExprTycker.Result tyckResult;
    public final @NotNull CodeOptions options;

    public Code(@NotNull Expr expr, @NotNull CodeOptions options) {
      this.expr = expr;
      this.options = options;
    }

    @Override @Contract(mutates = "this")
    public <P> void modify(@NotNull ExprFixpoint<P> fixpoint, P p) {
      expr = expr.accept(fixpoint, p);
    }

    @Override public <P> void visit(@NotNull ExprConsumer<P> consumer, P p) {
      expr.accept(consumer, p);
    }

    @Override public void tyck(@NotNull ExprTycker tycker) {
      tyckResult = tycker.zonk(expr, tycker.synthesize(expr));
    }

    @Override public @NotNull ImmutableSeq<TyckUnit> resolve(@NotNull ResolveInfo info, @NotNull Context context) {
      var resolver = new ExprResolver(ExprResolver.RESTRICTIVE);
      modify(resolver, context);
      return resolver.reference().toImmutableSeq();
    }

    private @NotNull Doc normalize(@NotNull Term term) {
      var mode = options.mode();
      return term.normalize(null, mode).toDoc(options.options());
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
}

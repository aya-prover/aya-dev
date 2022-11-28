// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark;

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
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.resolve.visitor.ExprResolver;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.tyck.order.TyckOrder;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author ice1000
 */
public sealed interface Literate extends Docile {
  default void modify(@NotNull Function<Expr, Expr> fixpoint) {
  }
  default void visit(@NotNull Consumer<Expr> consumer) {
  }
  default void tyck(@NotNull ExprTycker tycker) {
  }

  @NotNull ImmutableSeq<TyckOrder> resolve(@NotNull ResolveInfo info, @NotNull Context context);

  record Raw(@NotNull Doc toDoc) implements Literate {
    @Override public @NotNull ImmutableSeq<TyckOrder> resolve(@NotNull ResolveInfo info, @NotNull Context context) {
      return ImmutableSeq.empty();
    }
  }

  record Many(@Nullable Style style, @NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override public @NotNull ImmutableSeq<TyckOrder> resolve(@NotNull ResolveInfo info, @NotNull Context context) {
      children.forEach(child -> child.resolve(info, context));
      return ImmutableSeq.empty();
    }

    @Override public void modify(@NotNull Function<Expr, Expr> fixpoint) {
      children.forEach(literate -> literate.modify(fixpoint));
    }

    @Override public void visit(@NotNull Consumer<Expr> consumer) {
      children.forEach(child -> child.visit(consumer));
    }

    @Override public void tyck(@NotNull ExprTycker tycker) {
      children.forEach(literate -> literate.tyck(tycker));
    }

    @Override public @NotNull Doc toDoc() {
      var cat = Doc.cat(children.map(Literate::toDoc));
      return style == null ? cat : Doc.styled(style, cat);
    }
  }

  record Err(@NotNull MutableValue<AnyVar> def, @Override @NotNull SourcePos sourcePos) implements Literate {
    @Override public @NotNull ImmutableSeq<TyckOrder> resolve(@NotNull ResolveInfo info, @NotNull Context context) {
      def.set(context.getUnqualified(def.get().name(), sourcePos));
      // TODO: add to dependency?
      return ImmutableSeq.empty();
    }

    @Override public @NotNull Doc toDoc() {
      if (def.get() instanceof DefVar<?, ?> defVar && defVar.core instanceof UserDef<?> userDef) {
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
    public @Nullable TyckState state;
    public final @NotNull CodeOptions options;

    public Code(@NotNull Expr expr, @NotNull CodeOptions options) {
      this.expr = expr;
      this.options = options;
    }

    @Override @Contract(mutates = "this")
    public void modify(@NotNull Function<Expr, Expr> fixpoint) {
      expr = fixpoint.apply(expr);
    }

    @Override public void visit(@NotNull Consumer<Expr> consumer) {
      consumer.accept(expr);
    }

    @Override public void tyck(@NotNull ExprTycker tycker) {
      tyckResult = tycker.zonk(tycker.synthesize(expr));
      state = tycker.state;
    }

    @Override public @NotNull ImmutableSeq<TyckOrder> resolve(@NotNull ResolveInfo info, @NotNull Context context) {
      var resolver = new ExprResolver(context, ExprResolver.RESTRICTIVE);
      resolver.enterBody();
      modify(resolver);
      return resolver.reference().toImmutableSeq();
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

  record Unsupported(@NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override
    public @NotNull ImmutableSeq<TyckOrder> resolve(@NotNull ResolveInfo info, @NotNull Context context) {
      return ImmutableSeq.empty();
    }

    @Override
    public @NotNull Doc toDoc() {
      return Doc.vcat(children.map(Docile::toDoc));
    }
  }
}

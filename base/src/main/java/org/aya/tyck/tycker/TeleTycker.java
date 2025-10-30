// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableSeq;
import org.aya.normalize.Finalizer;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.ErrorTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.SortTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.syntax.telescope.Signature;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.UnifyError;
import org.aya.unify.Synthesizer;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public sealed interface TeleTycker {
  /**
   * Tyck a expr that is expected to be a type
   *
   * @return well-typed type or {@link ErrorTerm}
   */
  @Closed @NotNull Term checkType(@NotNull WithPos<Expr> typeExpr, boolean isResult);
  void addWithTerm(@NotNull Expr.WithTerm with, @NotNull SourcePos pos, @NotNull Term type);
  void putLocal(@NotNull LocalVar var, @Closed @NotNull Term type);

  /**
   * @return a locally nameless signature computed from what's in the localCtx.
   */
  @Contract(pure = true)
  default @NotNull Signature checkSignature(
    @NotNull ImmutableSeq<Expr.Param> cTele,
    @NotNull WithPos<Expr> result
  ) {
    var locals = cTele.view().map(Expr.Param::ref).toSeq();
    var checkedParam = checkTele(cTele);
    var checkedResult = checkType(result, true).bindTele(locals.view());
    // clearly telescope is Closed
    @Closed var telescope = new AbstractTele.Locns(checkedParam, checkedResult);
    return new Signature(telescope, cTele.map(Expr.Param::sourcePos));
  }

  /// Does not zonk the result. Need <emph>you</emph> to zonk them.
  ///
  /// @return a [Closed] telescope (with bound parameters), for the definition of [Closed], see [AbstractTele].
  default @NotNull ImmutableSeq<@Bound Param> checkTele(@NotNull ImmutableSeq<Expr.Param> cTele) {
    var tele = checkTeleFree(cTele);
    var locals = cTele.map(Expr.Param::ref);
    // the **whole** tele is now Closed after `bindTele`
    AbstractTele.bindTele(locals, tele);
    return tele.toSeq();
  }

  /// Check the tele with free variables remaining in the localCtx.
  /// Does not zonk!
  @Contract(pure = true)
  default @NotNull MutableSeq<Param> checkTeleFree(ImmutableSeq<Expr.Param> cTele) {
    return MutableSeq.from(cTele.view().map(p -> {
      @Closed var pTy = checkType(p.typeExpr(), false);
      addWithTerm(p, p.sourcePos(), pTy);
      putLocal(p.ref(), pTy);
      return new Param(p.ref().name(), pTy, p.explicit());
    }));
  }

  @Contract(mutates = "param3")
  static void loadTele(
    @NotNull SeqView<LocalVar> binds,
    @NotNull Signature signature,
    @NotNull ExprTycker tycker
  ) {
    assert binds.sizeEquals(signature.telescope().telescopeSize());
    var tele = MutableList.<LocalVar>create();

    binds.forEachWith(signature.params(), (ref, param) -> {
      var ty = param.type().instTeleVar(tele.view());
      tycker.localCtx().put(ref, ty);
      if (ty instanceof ClassCall clazz) {
        tycker.instanceSet.putParam(ref, clazz);
      }
      tele.append(ref);
    });
  }

  sealed interface Impl extends TeleTycker {
    @NotNull ExprTycker tycker();
    @Override default void putLocal(@NotNull LocalVar var, @NotNull Term type) {
      tycker().localCtx().put(var, type);
      if (type instanceof ClassCall clazz) {
        tycker().instanceSet.putParam(var, clazz);
      }
    }
  }

  record Default(@Override @NotNull ExprTycker tycker) implements Impl {
    @Override public @NotNull Term checkType(@NotNull WithPos<Expr> typeExpr, boolean isResult) {
      return tycker.ty(typeExpr);
    }
    @Override public void addWithTerm(Expr.@NotNull WithTerm with, @NotNull SourcePos pos, @NotNull Term type) {
      tycker.addWithTerm(with, pos, type);
    }
  }

  final class InlineCode implements Impl {
    private final @NotNull ExprTycker tycker;
    public Jdg result;
    public InlineCode(@NotNull ExprTycker tycker) { this.tycker = tycker; }
    public @NotNull Jdg checkInlineCode(@NotNull ImmutableSeq<Expr.Param> params, @NotNull WithPos<Expr> expr) {
      checkSignature(params, expr);
      tycker.solveMetas();
      var zonker = new Finalizer.Zonk<>(tycker);
      return result.map(zonker::zonk);
    }
    @Override public @NotNull Term checkType(@NotNull WithPos<Expr> typeExpr, boolean isResult) {
      if (!isResult) return tycker.ty(typeExpr);
      var jdg = tycker.synthesize(typeExpr);
      result = jdg;
      return jdg.wellTyped();
    }
    @Override public void addWithTerm(Expr.@NotNull WithTerm with, @NotNull SourcePos pos, @NotNull Term type) {
      tycker.addWithTerm(with, pos, type);
    }
    @Override public @NotNull ExprTycker tycker() { return tycker; }
  }

  record Con(@NotNull ExprTycker tycker, @NotNull SortTerm dataResult) implements Impl {
    @Override public @NotNull Term checkType(@NotNull WithPos<Expr> typeExpr, boolean isResult) {
      @Closed var result = tycker.ty(typeExpr);
      if (!new Synthesizer(tycker).inheritPiDom(result, dataResult)) {
        tycker.fail(new UnifyError.PiDom(typeExpr.data(), typeExpr.sourcePos(), result, dataResult));
      }
      return result;
    }
    @Override public void addWithTerm(Expr.@NotNull WithTerm with, @NotNull SourcePos pos, @NotNull Term type) {
      tycker.addWithTerm(with, pos, type);
    }
  }
}

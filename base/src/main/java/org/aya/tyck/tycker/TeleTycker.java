// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.def.Signature;
import org.aya.syntax.core.term.ErrorTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.SortTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.UnifyError;
import org.aya.unify.Synthesizer;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public sealed interface TeleTycker extends Contextful {
  /**
   * Tyck a expr that is expected to be a type
   *
   * @return well-typed type or {@link ErrorTerm}
   */
  @NotNull Term checkType(@NotNull WithPos<Expr> typeExpr);

  /**
   * @return a locally nameless signature computed from what's in the localCtx.
   */
  @Contract(pure = true)
  default @NotNull Signature checkSignature(
    @NotNull ImmutableSeq<Expr.Param> cTele,
    @NotNull WithPos<Expr> result
  ) {
    var locals = cTele.view().map(Expr.Param::ref).toImmutableSeq();
    var checkedParam = checkTele(cTele);
    var checkedResult = checkType(result).bindTele(locals.view());
    return new Signature(checkedParam, checkedResult);
  }

  /**
   * Does not zonk the result. Need <emph>you</emph> to zonk them.
   */
  default @NotNull ImmutableSeq<WithPos<Param>> checkTele(@NotNull ImmutableSeq<Expr.Param> cTele) {
    var tele = checkTeleFree(cTele);
    var locals = cTele.view().map(Expr.Param::ref).toImmutableSeq();
    bindTele(locals, tele);
    return tele.zip(cTele, (param, sp) -> new WithPos<>(sp.sourcePos(), param))
      .toImmutableSeq();
  }

  /**
   * Check the tele with free variables remaining in the localCtx.
   * Does not zonk!
   */
  @Contract(pure = true)
  default @NotNull MutableSeq<Param> checkTeleFree(ImmutableSeq<Expr.Param> cTele) {
    return MutableSeq.from(cTele.view().map(p -> {
      var pTy = checkType(p.typeExpr());
      localCtx().put(p.ref(), pTy);
      return new Param(p.ref().name(), pTy, p.explicit());
    }));
  }

  /**
   * Replace {@link org.aya.syntax.core.term.FreeTerm} in {@param tele} with appropriate index
   */
  @Contract(mutates = "param2")
  static void bindTele(ImmutableSeq<LocalVar> binds, MutableSeq<Param> tele) {
    final var lastIndex = tele.size() - 1;
    // fix some param, say [p]
    for (int i = lastIndex - 1; i >= 0; i--) {
      var p = binds.get(i);
      // for any other param that is able to refer to [p]
      for (int j = i + 1; j < tele.size(); j++) {
        var og = tele.get(j);
        // j - i is the human distance between [p] and [og]. However, we count from 0
        int ii = i, jj = j;
        tele.set(j, og.descent(x -> x.bindAt(p, jj - ii - 1)));
      }
    }
  }

  @Contract(mutates = "param3")
  static void loadTele(
    @NotNull SeqView<LocalVar> binds,
    @NotNull Signature signature,
    @NotNull ExprTycker tycker
  ) {
    assert binds.sizeEquals(signature.param());
    var tele = MutableList.<LocalVar>create();

    binds.forEachWith(signature.param(), (ref, param) -> {
      tycker.localCtx().put(ref, param.data().type().instantiateTeleVar(tele.view()));
      tele.append(ref);
    });
  }

  sealed interface Impl extends TeleTycker {
    @NotNull ExprTycker tycker();
    @Override default @NotNull LocalCtx localCtx() { return tycker().localCtx(); }
    @Override default @NotNull LocalCtx setLocalCtx(@NotNull LocalCtx ctx) { return tycker().setLocalCtx(ctx); }
  }

  record Default(@Override @NotNull ExprTycker tycker) implements Impl {
    @Override public @NotNull Term checkType(@NotNull WithPos<Expr> typeExpr) {
      return tycker.ty(typeExpr);
    }
  }

  record Con(@NotNull ExprTycker tycker, @NotNull SortTerm dataResult) implements Impl {
    @Override
    public @NotNull Term checkType(@NotNull WithPos<Expr> typeExpr) {
      var result = tycker.ty(typeExpr);
      if (!new Synthesizer(tycker).inheritPiDom(result, dataResult)) {
        tycker.fail(new UnifyError.PiDom(typeExpr.data(), typeExpr.sourcePos(), result, dataResult));
      }
      return result;
    }
  }
}

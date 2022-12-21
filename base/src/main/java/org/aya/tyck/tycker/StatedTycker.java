// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.collection.mutable.MutableTreeSet;
import kala.value.LazyValue;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.*;
import org.aya.core.term.*;
import org.aya.core.visitor.Zonker;
import org.aya.generic.Modifier;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Partial;
import org.aya.ref.DefVar;
import org.aya.tyck.Result;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.Unifier;
import org.aya.util.Ordering;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This is the second base-base class of a tycker.
 * It has the zonking stuffs and basic def-call related functions.
 * Apart from that, it also deals with core term references in concrete terms.
 *
 * @author ice1000
 * @see #addWithTerm
 * @see #zonk
 * @see #solveMetas()
 * @see #traceExit
 * @see #whnf(Term)
 * @see #defCall
 * @see #inferRef(DefVar)
 */
public abstract sealed class StatedTycker extends TracedTycker permits MockedTycker {
  public final @NotNull TyckState state;
  public final @NotNull MutableTreeSet<Expr.WithTerm> withTerms =
    MutableTreeSet.create(Comparator.comparing(SourceNode::sourcePos));

  protected StatedTycker(@NotNull Reporter reporter, @Nullable Trace.Builder traceBuilder, @NotNull TyckState state) {
    super(reporter, traceBuilder);
    this.state = state;
  }

  //region Zonk + solveMetas
  public @NotNull Term zonk(@NotNull Term term) {
    solveMetas();
    return Zonker.make(this).apply(term);
  }

  public @NotNull Result zonk(@NotNull Result result) {
    return new Result.Default(zonk(result.wellTyped()), zonk(result.type()));
  }

  public @NotNull Partial<Term> zonk(@NotNull Partial<Term> term) {
    solveMetas();
    return term.fmap(Zonker.make(this));
  }

  public void solveMetas() {
    state.solveMetas(reporter, traceBuilder);
    withTerms.forEach(w -> w.theCore().update(r -> r.freezeHoles(state)));
  }

  public @NotNull Term whnf(@NotNull Term term) {
    return term.normalize(state, NormalizeMode.WHNF);
  }

  protected final @NotNull <D extends Def, S extends Decl & Decl.Telescopic<?>> Result defCall(DefVar<D, S> defVar, Callable.Factory<D, S> function) {
    var tele = Def.defTele(defVar);
    var teleRenamed = tele.map(org.aya.core.term.Term.Param::rename);
    // unbound these abstracted variables
    Term body = function.make(defVar, 0, teleRenamed.map(org.aya.core.term.Term.Param::toArg));
    var type = PiTerm.make(tele, Def.defResult(defVar)).rename();
    if ((defVar.core instanceof FnDef fn && fn.modifiers.contains(Modifier.Inline)) || defVar.core instanceof PrimDef) {
      body = whnf(body);
    }
    return new Result.Default(LamTerm.make(teleRenamed, body), type);
  }

  @SuppressWarnings("unchecked") protected final @NotNull Result inferRef(@NotNull DefVar<?, ?> var) {
    if (var.core instanceof FnDef || var.concrete instanceof TeleDecl.FnDecl) {
      return defCall((DefVar<FnDef, TeleDecl.FnDecl>) var, FnCall::new);
    } else if (var.core instanceof PrimDef) {
      return defCall((DefVar<PrimDef, TeleDecl.PrimDecl>) var, PrimCall::new);
    } else if (var.core instanceof DataDef || var.concrete instanceof TeleDecl.DataDecl) {
      return defCall((DefVar<DataDef, TeleDecl.DataDecl>) var, DataCall::new);
    } else if (var.core instanceof StructDef || var.concrete instanceof TeleDecl.StructDecl) {
      return defCall((DefVar<StructDef, TeleDecl.StructDecl>) var, StructCall::new);
    } else if (var.core instanceof CtorDef || var.concrete instanceof TeleDecl.DataDecl.DataCtor) {
      var conVar = (DefVar<CtorDef, TeleDecl.DataDecl.DataCtor>) var;
      var tele = Def.defTele(conVar);
      var type = PiTerm.make(tele, Def.defResult(conVar)).rename();
      var telescopes = new DataDef.CtorTelescopes(conVar.core);
      return new Result.Default(telescopes.toConCall(conVar, 0), type);
    } else if (var.core instanceof FieldDef || var.concrete instanceof TeleDecl.StructField) {
      // the code runs to here because we are tycking a StructField in a StructDecl
      // there should be two-stage check for this case:
      //  - check the definition's correctness: happens here
      //  - check the field value's correctness: happens in `visitNew` after the body was instantiated
      var field = (DefVar<FieldDef, TeleDecl.StructField>) var;
      return new Result.Default(new RefTerm.Field(field), Def.defType(field));
    } else {
      final var msg = "Def var `" + var.name() + "` has core `" + var.core + "` which we don't know.";
      throw new InternalException(msg);
    }
  }

  public @NotNull Unifier unifier(@NotNull SourcePos pos, @NotNull Ordering ord, @NotNull LocalCtx ctx) {
    return new Unifier(ord, reporter, false, true, traceBuilder, state, pos, ctx);
  }

  protected final <R extends Result> R traced(
    @NotNull Supplier<Trace> trace,
    @NotNull Expr expr, @NotNull Function<Expr, R> tyck
  ) {
    tracing(builder -> builder.shift(trace.get()));
    var result = tyck.apply(expr);
    traceExit(result, expr);
    return result;
  }

  protected final void traceExit(Result result, @NotNull Expr expr) {
    var frozen = LazyValue.of(() -> result.freezeHoles(state));
    tracing(builder -> {
      builder.append(new Trace.TyckT(frozen.get(), expr.sourcePos()));
      builder.reduce();
    });
    if (expr instanceof Expr.WithTerm wt) addWithTerm(wt, frozen.get());
    if (expr instanceof Expr.Lift lift && lift.expr() instanceof Expr.WithTerm wt) addWithTerm(wt, frozen.get());
  }

  protected final void addWithTerm(@NotNull Expr.WithTerm withTerm, @NotNull Result result) {
    withTerms.add(withTerm);
    withTerm.theCore().set(result);
  }

  public final void addWithTerm(@NotNull Expr.Param param, @NotNull Term type) {
    addWithTerm(param, new Result.Default(new RefTerm(param.ref()), type));
  }
}

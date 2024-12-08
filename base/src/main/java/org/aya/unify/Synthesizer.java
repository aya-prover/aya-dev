// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.unify;

import org.aya.generic.Renamer;
import org.aya.generic.term.DTKind;
import org.aya.generic.term.SortKind;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.Callable;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.core.term.repr.ListTerm;
import org.aya.syntax.core.term.repr.MetaLitTerm;
import org.aya.syntax.core.term.repr.StringTerm;
import org.aya.syntax.core.term.xtt.*;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MetaVar;
import org.aya.tyck.TyckState;
import org.aya.tyck.tycker.AbstractTycker;
import org.aya.tyck.tycker.Contextful;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Synthesizer(
  @NotNull Renamer renamer,
  @NotNull AbstractTycker tycker
) implements Stateful, Contextful {
  public Synthesizer(@NotNull AbstractTycker tycker) {
    this(new Renamer(), tycker);
    renamer.store(tycker.localCtx());
  }

  public boolean inheritPiDom(@NotNull Term ty, @NotNull SortTerm expected) {
    if (ty instanceof MetaCall meta && meta.ref().req() == MetaVar.Misc.IsType) {
      var typed = meta.asPiDom(expected);
      // The old code checks recursion in solve, now we don't, but it's okay,
      // since it is impossible for this `solve` to fail.
      solve(meta.ref(), typed);
      return true;
    }

    if (!(trySynth(ty) instanceof SortTerm(var kind, int lift))) return false;
    return switch (kind) {
      case Type -> expected.kind() == SortKind.Type && lift <= expected.lift();
      case Set -> expected.kind() == SortKind.Set && lift <= expected.lift();
      case ISet -> expected.kind() == SortKind.Type;
    };
  }

  public @Nullable Term trySynth(@NotNull Term term) {
    var result = synthesize(term);
    return result == null ? null : whnf(result);
  }

  public @NotNull Term synth(@NotNull Term term) {
    var result = trySynth(term);
    assert result != null : term.debuggerOnlyToString() + " : " + term.getClass();
    return result;
  }

  public @NotNull Term synthDontNormalize(@NotNull Term term) {
    var result = synthesize(term);
    assert result != null : term.debuggerOnlyToString() + " : " + term.getClass();
    return result;
  }

  /**
   * @param term a whnfed term
   * @return type of term if success
   */
  private @Nullable Term synthesize(@NotNull Term term) {
    return switch (term) {
      case AppTerm(var f, var a) -> trySynth(f) instanceof DepTypeTerm pi ? pi.body().apply(a) : null;
      case DepTypeTerm(var kind, var piParam, var body) -> {
        if (!(trySynth(piParam) instanceof SortTerm pSort)) yield null;
        var bTy = tycker.subscoped(piParam, param ->
          trySynth(body.apply(param)), renamer);

        if (!(bTy instanceof SortTerm bSort)) yield null;
        yield switch (kind) {
          case Pi -> DepTypeTerm.lubPi(pSort, bSort);
          case Sigma -> DepTypeTerm.lubSigma(pSort, bSort);
        };
      }
      case TupTerm _, LamTerm _ -> null;
      case FreeTerm(var var) -> localCtx().get(var);
      case LocalTerm _ -> Panic.unreachable();
      case MetaPatTerm meta -> meta.meta().type();
      case ProjTerm(var of, var fst) -> {
        var ofTy = trySynth(of);
        if (!(ofTy instanceof DepTypeTerm(var kind, var lhs, var rhs) && kind == DTKind.Sigma)) yield null;
        yield fst ? lhs : rhs.apply(ProjTerm.fst(of));
      }
      case IntegerTerm lit -> lit.type();
      case ListTerm list -> list.type();
      case ConCall conCall -> conCall.ref().signature().result(conCall.args());
      case Callable.Tele teleCall -> teleCall.ref().signature()
        .result(teleCall.args())
        .elevate(teleCall.ulift());

      case MetaCall(var ref, var args) when ref.req() instanceof MetaVar.OfType(var type) ->
        type.instantiateTele(args.view());
      case MetaCall meta -> {
        if (!state().solutions.containsKey(meta.ref())) yield null;
        yield trySynth(whnf(meta));
      }
      case CoeTerm coe -> coe.family();
      case EqTerm eq -> trySynth(eq.appA(DimTerm.I0));
      case PAppTerm papp -> {
        if (!(trySynth(papp.fun()) instanceof EqTerm eq)) yield null;
        yield eq.appA(papp.arg());
      }
      case ErrorTerm error -> ErrorTerm.typeOf(error);
      case SortTerm sort -> sort.succ();
      case DimTerm _ -> DimTyTerm.INSTANCE;
      case DimTyTerm _ -> SortTerm.ISet;
      case MetaLitTerm mlt -> mlt.type();
      case StringTerm _ -> state().primFactory.getCall(PrimDef.ID.STRING);
      case ClassCall classCall -> classCall.ref().members().view()
        .drop(classCall.args().size())
        .foldLeft(SortTerm.Type0, (acc, mem) -> DepTypeTerm.lubSigma(acc, mem.type()));
      case NewTerm newTerm -> newTerm.inner();
      case ClassCastTerm castTerm -> new ClassCall(castTerm.ref(), 0, castTerm.remember());
    };
  }

  public @NotNull Term mkFree(@NotNull Term type) {
    var param = LocalVar.generate(Renamer.nameOf(type));
    localCtx().put(param, type);
    return new FreeTerm(param);
  }
  @Override public @NotNull TyckState state() { return tycker.state; }
  @Override public @NotNull LocalCtx localCtx() { return tycker.localCtx(); }
  @Override public @NotNull LocalCtx setLocalCtx(@NotNull LocalCtx ctx) {
    return tycker.setLocalCtx(ctx);
  }

  public boolean isTypeMeta(@NotNull MetaVar.Requirement req) {
    return switch (req) {
      case MetaVar.Misc misc -> switch (misc) {
        case Whatever -> false;
        case IsType -> true;
      };
      case MetaVar.OfType(var type) -> trySynth(type) instanceof SortTerm;
      case MetaVar.PiDom _ -> true;
    };
  }
}

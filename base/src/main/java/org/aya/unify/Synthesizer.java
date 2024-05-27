// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.unify;

import kala.collection.mutable.MutableList;
import org.aya.generic.NameGenerator;
import org.aya.generic.term.SortKind;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.Callable;
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
  @NotNull NameGenerator nameGen,
  @NotNull AbstractTycker tycker
) implements Stateful, Contextful {
  public Synthesizer(@NotNull AbstractTycker tycker) {
    this(new NameGenerator(), tycker);
  }

  public boolean inheritPiDom(@NotNull Term ty, @NotNull SortTerm expected) {
    if (ty instanceof MetaCall meta && meta.ref().req() == MetaVar.Misc.IsType) {
      var typed = meta.asPiDom(expected);
      // The old code checks recursion in solve, now we don't, but it's okay,
      // since it is impossible for this `solve` to fail.
      solve(meta.ref(), typed);
      return true;
    }

    if (!(trySynth(ty) instanceof SortTerm tyty)) return false;
    return switch (tyty.kind()) {
      case Type -> expected.kind() == SortKind.Type && tyty.lift() <= expected.lift();
      case Set -> expected.kind() == SortKind.Set && tyty.lift() <= expected.lift();
      case ISet -> Panic.unreachable();
    };
  }

  public @Nullable Term trySynth(@NotNull Term term) {
    var result = synthesize(term);
    return result == null ? null : whnf(result);
  }

  public @NotNull Term synth(@NotNull Term term) {
    var result = trySynth(term);
    assert result != null : term.toDoc(AyaPrettierOptions.debug()).debugRender();
    return result;
  }

  public @NotNull Term synthDontNormalize(@NotNull Term term) {
    var result = synthesize(term);
    assert result != null : term.toDoc(AyaPrettierOptions.debug()).debugRender();
    return result;
  }

  /**
   * @param term a whnfed term
   * @return type of term if success
   */
  private @Nullable Term synthesize(@NotNull Term term) {
    return switch (term) {
      case AppTerm(var f, var a) -> trySynth(f) instanceof PiTerm pi ? pi.body().apply(a) : null;
      case PiTerm pi -> {
        if (!(trySynth(pi.param()) instanceof SortTerm pSort)) yield null;
        var bTy = subscoped(() -> {
          var param = putIndex(pi.param());
          return trySynth(pi.body().apply(param));
        });

        if (!(bTy instanceof SortTerm bSort)) yield null;
        yield PiTerm.lub(pSort, bSort);
      }
      case SigmaTerm sigma -> {
        var pTys = MutableList.<SortTerm>create();
        boolean succ = subscoped(() -> {
          for (var p : sigma.view(i -> new FreeTerm(putIndex(i)))) {
            if (!(trySynth(p) instanceof SortTerm pSort)) return false;
            pTys.append(pSort);
          }
          return true;
        });
        if (!succ) yield null;

        // This is safe since a [SigmaTerm] has at least 2 parameters.
        yield pTys.reduce(SigmaTerm::lub);
      }
      case TupTerm _, LamTerm _ -> null;
      case FreeTerm(var var) -> localCtx().get(var);
      case LocalTerm _ -> Panic.unreachable();
      case MetaPatTerm meta -> meta.meta().type();
      case ProjTerm(Term of, int index) -> {
        var ofTy = trySynth(of);
        if (!(ofTy instanceof SigmaTerm ofSigma)) yield null;
        yield ofSigma.params().get(index - 1)
          // the type of projOf.{index - 1} may refer to the previous parameters
          .instantiateTele(ProjTerm.projSubst(of, index).view());
      }
      case IntegerTerm lit -> lit.type();
      case ListTerm list -> list.type();
      case Callable.Tele teleCall -> TyckDef.defSignature(teleCall.ref())
        .result(teleCall.args())
        .elevate(teleCall.ulift());

      case MetaCall(var ref, var args) when ref.req() instanceof MetaVar.OfType(var type) ->
        type.instantiateTele(args.view());
      case MetaCall meta -> {
        if (!state().solutions().containsKey(meta.ref())) yield null;
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
      case StringTerm str -> state().primFactory().getCall(PrimDef.ID.STRING);
    };
  }

  public @NotNull LocalVar putIndex(@NotNull Term type) {
    return tycker.putIndex(nameGen, type);
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
      case MetaVar.OfType (var type) -> trySynth(type) instanceof SortTerm;
      case MetaVar.PiDom _ -> true;
    };
  }
}

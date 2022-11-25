// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.term.*;
import org.aya.core.visitor.DeltaExpander;
import org.aya.core.visitor.Subst;
import org.aya.generic.Constants;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Partial;
import org.aya.tyck.env.LocalCtx;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

/**
 * Similar to <code>GetTypeVisitor</code> in Arend.
 *
 * @author ice1000
 */
public record LittleTyper(@NotNull TyckState state, @NotNull LocalCtx localCtx) {
  public @NotNull Term term(@NotNull Term preterm) {
    return switch (preterm) {
      case RefTerm term -> localCtx.get(term.var());
      case ConCall conCall -> conCall.head().underlyingDataCall();
      case Callable.DefCall call -> defCall(call);
      case MetaTerm hole -> {
        var result = hole.ref().result;
        yield result == null ? ErrorTerm.typeOf(hole) : result;
      }
      case ErrorTerm term -> ErrorTerm.typeOf(term);
      case RefTerm.Field field -> Def.defType(field.ref());
      case FieldTerm access -> {
        var callRaw = whnf(term(access.of()));
        if (!(callRaw instanceof StructCall call)) yield ErrorTerm.typeOf(access);
        var field = access.ref();
        var subst = DeltaExpander.buildSubst(Def.defTele(field), access.fieldArgs())
          .add(DeltaExpander.buildSubst(Def.defTele(call.ref()), access.structArgs()));
        yield Def.defResult(field).subst(subst);
      }
      case SigmaTerm sigma -> {
        var univ = sigma.params().view()
          .map(param -> whnf(term(param.type())))
          .filterIsInstance(SortTerm.class)
          .toImmutableSeq();
        if (univ.sizeEquals(sigma.params().size())) {
          try {
            yield univ.reduce(SigmaTerm::max);
          } catch (IllegalArgumentException ignored) {
            yield ErrorTerm.typeOf(sigma);
          }
        } else {
          yield ErrorTerm.typeOf(sigma);
        }
      }
      case LamTerm lambda -> new PiTerm(lambda.param(), term(lambda.body()));
      case ProjTerm proj -> {
        var sigmaRaw = whnf(term(proj.of()));
        if (!(sigmaRaw instanceof SigmaTerm sigma)) yield ErrorTerm.typeOf(proj);
        var index = proj.ix() - 1;
        var telescope = sigma.params();
        yield telescope.get(index).type()
          .subst(ProjTerm.projSubst(proj.of(), index, telescope));
      }
      case NewTerm neu -> neu.struct();
      case TupTerm tuple -> new SigmaTerm(tuple.items().map(item ->
        new Term.Param(Constants.anonymous(), term(item.term()), item.explicit())));
      case MetaPatTerm metaPat -> metaPat.ref().type();
      case MetaLitTerm lit -> lit.type();
      case PiTerm pi -> {
        var paramTyRaw = whnf(term(pi.param().type()));
        var resultParam = new Term.Param(pi.param().ref(), whnf(pi.param().type()), pi.param().explicit());
        var t = new LittleTyper(state, localCtx.deriveMap());
        yield t.localCtx.with(resultParam, () -> {
          var retTyRaw = whnf(t.term(pi.body()));
          if (paramTyRaw instanceof SortTerm paramTy && retTyRaw instanceof SortTerm retTy) {
            try {
              return ExprTycker.sortPi(paramTy, retTy);
            } catch (IllegalArgumentException ignored) {
              return ErrorTerm.typeOf(pi);
            }
          } else {
            return ErrorTerm.typeOf(pi);
          }
        });
      }
      case AppTerm app -> {
        var piRaw = whnf(term(app.of()));
        yield piRaw instanceof PiTerm pi ? pi.substBody(app.arg().term()) : ErrorTerm.typeOf(app);
      }
      case MatchTerm match -> {
        // TODO: Should I normalize match.discriminant() before matching?
        var term = match.tryMatch();
        yield term.isDefined() ? term(term.get()) : ErrorTerm.typeOf(match);
      }
      case SortTerm sort -> sort.succ();
      case IntervalTerm interval -> SortTerm.Type0;
      case FormulaTerm end -> IntervalTerm.INSTANCE;
      case StringTerm str -> state.primFactory().getCall(PrimDef.ID.STRING);
      case IntegerTerm shaped -> shaped.type();
      case ListTerm shaped -> shaped.type();
      case PartialTyTerm ty -> term(ty.type());
      case PartialTerm el -> new PartialTyTerm(el.rhsType(), el.partial().restr());
      case PathTerm cube -> term(cube.type());
      case PLamTerm lam -> new PathTerm(
        lam.params(),
        term(lam.body()),
        new Partial.Const<>(term(lam.body())));
      case PAppTerm app -> {
        // v @ ui : A[ui/xi]
        var xi = app.cube().params();
        var ui = app.args().map(Arg::term);
        yield app.cube().type().subst(new Subst(xi, ui));
      }
      case CoeTerm coe -> PrimDef.familyLeftToRight(coe.type());
      case HCompTerm hComp -> throw new InternalException("TODO");
      case SubTerm subTerm -> throw new InternalException("TODO");
      case InSTerm inSTerm -> throw new InternalException("TODO");
      case OutSTerm outSTerm -> throw new InternalException("TODO");
    };
  }

  private @NotNull Term whnf(Term x) {
    return x.normalize(state, NormalizeMode.WHNF);
  }

  private @NotNull Term defCall(@NotNull Callable.DefCall call) {
    return Def.defResult(call.ref())
      .subst(DeltaExpander.buildSubst(Def.defTele(call.ref()), call.args()))
      .lift(call.ulift());
  }
}

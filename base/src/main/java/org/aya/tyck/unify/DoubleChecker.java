// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.unify;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.term.*;
import org.aya.core.visitor.DeltaExpander;
import org.aya.core.visitor.Subst;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Partial;
import org.aya.tyck.error.BadExprError;
import org.aya.tyck.error.TupleError;
import org.aya.tyck.unify.TermComparator.Sub;
import org.aya.util.Arg;
import org.aya.util.Ordering;
import org.jetbrains.annotations.NotNull;

public record DoubleChecker(@NotNull Unifier unifier, @NotNull Sub lr, @NotNull Sub rl) {
  public DoubleChecker {
    assert unifier.cmp == Ordering.Lt;
  }

  public DoubleChecker(@NotNull Unifier unifier) {
    this(unifier, new Sub(), new Sub());
  }

  public @NotNull Term synthesize(@NotNull Term preterm) {
    return whnf(switch (preterm) {
      case RefTerm term -> unifier.ctx.get(term.var());
      case ConCall conCall -> conCall.head().underlyingDataCall();
      case Callable.DefCall call -> Def.defResult(call.ref())
        .subst(DeltaExpander.buildSubst(Def.defTele(call.ref()), call.args()))
        .lift(call.ulift());
      // TODO: deal with type-only metas
      case MetaTerm hole -> hole.ref().result;
      case RefTerm.Field field -> Def.defType(field.ref());
      case FieldTerm access -> {
        var callRaw = synthesize(preterm);
        if (!(callRaw instanceof StructCall call)) yield unreachable(callRaw);
        var field = access.ref();
        var subst = DeltaExpander.buildSubst(Def.defTele(field), access.fieldArgs())
          .add(DeltaExpander.buildSubst(Def.defTele(call.ref()), access.structArgs()));
        yield Def.defResult(field).subst(subst);
      }
      case NewTerm neu -> neu.struct();
      case ErrorTerm term -> ErrorTerm.typeOf(term.description());
      case ProjTerm proj -> {
        var sigmaRaw = synthesize(proj.of());
        if (!(sigmaRaw instanceof SigmaTerm sigma)) yield ErrorTerm.typeOf(proj);
        var index = proj.ix() - 1;
        var telescope = sigma.params();
        yield telescope.get(index).type().subst(ProjTerm.projSubst(proj.of(), index, telescope));
      }
      case MetaPatTerm metaPat -> metaPat.ref().type();
      case MetaLitTerm lit -> lit.type();
      case SortTerm sort -> sort.succ();
      case IntervalTerm interval -> SortTerm.Type0;
      case FormulaTerm end -> IntervalTerm.INSTANCE;
      case StringTerm str -> unifier.state.primFactory().getCall(PrimDef.ID.STRING);
      case IntegerTerm shaped -> shaped.type();
      case ListTerm shaped -> shaped.type();
      case PartialTyTerm ty -> synthesize(ty.type());
      case PartialTerm(var rhs, var par) -> new PartialTyTerm(par, rhs.restr());
      case PathTerm cube -> synthesize(cube.type());
      case MatchTerm match -> {
        // TODO: Should I normalize match.discriminant() before matching?
        var term = match.tryMatch();
        yield term.isDefined() ? synthesize(term.get()) : ErrorTerm.typeOf(match);
      }
      case CoeTerm coe -> PrimDef.familyLeftToRight(coe.type());
      case HCompTerm hComp -> throw new InternalException("TODO");
      case InOutTerm inS when inS.kind() == InOutTerm.Kind.In -> {
        var ty = synthesize(inS.u());
        yield unifier.state.primFactory().getCall(PrimDef.ID.SUB, ImmutableSeq.of(
            ty, inS.phi(), PartialTerm.from(inS.phi(), inS.u(), ty))
          .map(t -> new Arg<>(t, true)));
      }
      case InOutTerm outS -> {
        var ty = synthesize(outS.u());
        if (ty instanceof PrimCall sub) yield sub.args().first().term();
        else yield ErrorTerm.typeOf(outS);
      }
      case PAppTerm app -> {
        // v @ ui : A[ui/xi]
        var xi = app.cube().params();
        var ui = app.args().map(Arg::term);
        yield app.cube().type().subst(new Subst(xi, ui));
      }
      case PLamTerm lam -> {
        var bud = synthesize(lam.body());
        yield new PathTerm(lam.params(), bud, new Partial.Const<>(bud));
      }
      case AppTerm app -> {
        var piRaw = synthesize(app.of());
        yield piRaw instanceof PiTerm pi ? pi.substBody(app.arg().term()) : unreachable(piRaw);
      }
      case default -> unreachable(preterm);
    });
  }

  private static <T> T unreachable(@NotNull Term preterm) {
    throw new AssertionError("Unexpected term: " + preterm);
  }

  private @NotNull Term whnf(Term x) {
    return x.normalize(unifier.state, NormalizeMode.WHNF);
  }

  public boolean inherit(@NotNull Term preterm, @NotNull Term expected) {
    return switch (preterm) {
      case ErrorTerm term -> true;
      case SigmaTerm sigma -> sigma.params().view()
        .allMatch(param -> inherit(param.type(), expected));
      case LamTerm(var param, var body)when whnf(expected) instanceof PiTerm(var tparam, var tbody) ->
        unifier.ctx.with(param.ref(), tparam.type(), () -> inherit(body, tbody));
      case LamTerm lambda -> {
        unifier.reporter.report(new BadExprError(lambda, unifier.pos, expected));
        yield false;
      }
      case TupTerm(var items)when whnf(expected) instanceof SigmaTerm sigma -> {
        var res = sigma.check(items, (e, t) -> {
          if (!inherit(e.term(), t)) return ErrorTerm.unexpected(e.term());
          return e.term();
        });
        if (res == null) unifier.reporter.report(new TupleError.ElemMismatchError(
          unifier.pos, sigma.params().size(), items.size()));
        yield res != null && res.items().allMatch(i -> !(i.term() instanceof ErrorTerm));
      }
      case PiTerm(var dom, var cod) -> {
        var domSort = synthesize(dom.type());
        // TODO^: make sure the above is a type. Need an extra "isType"
        yield inherit(cod, expected);
      }
      case default -> unifier.compare(synthesize(preterm), expected, lr, rl, null);
    };
  }
}

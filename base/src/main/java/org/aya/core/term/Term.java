// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.Map;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import kala.tuple.Tuple3;
import org.aya.core.pat.Pat;
import org.aya.core.visitor.*;
import org.aya.distill.BaseDistiller;
import org.aya.distill.CoreDistiller;
import org.aya.generic.Arg;
import org.aya.generic.AyaDocile;
import org.aya.generic.ParamLike;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Restr;
import org.aya.pretty.doc.Doc;
import org.aya.ref.Bind;
import org.aya.ref.LocalVar;
import org.aya.ref.Var;
import org.aya.tyck.LittleTyper;
import org.aya.tyck.TyckState;
import org.aya.tyck.env.LocalCtx;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * A well-typed and terminating term.
 *
 * @author ice1000
 */
public sealed interface Term extends AyaDocile, Restr.TermLike<Term> permits CallTerm, ElimTerm, ErrorTerm,
  FormTerm, IntroTerm, PrimTerm, RefTerm, RefTerm.Field, RefTerm.MetaPat, LitTerm {

  default @NotNull Term descent(@NotNull Function<@NotNull Term, @NotNull Term> f) {
    return switch (this) {
      case FormTerm.Pi pi -> {
        var param = pi.param().descent(f);
        var body = f.apply(pi.body());
        if (param == pi.param() && body == pi.body()) yield pi;
        yield new FormTerm.Pi(param, body);
      }
      case FormTerm.Sigma sigma -> {
        var params = sigma.params().map(param -> param.descent(f));
        if (params.sameElements(sigma.params(), true)) yield sigma;
        yield new FormTerm.Sigma(params);
      }
      case FormTerm.Univ univ -> univ;
      case FormTerm.Interval interval -> interval;
      case PrimTerm.Mula mula -> {
        var formula = mula.asFormula().fmap(f);
        if (formula == mula.asFormula()) yield mula;
        yield new PrimTerm.Mula(formula);
      }
      case PrimTerm.Str str -> str;
      case IntroTerm.Lambda lambda -> {
        var param = lambda.param().descent(f);
        var body = f.apply(lambda.body());
        if (param == lambda.param() && body == lambda.body()) yield lambda;
        yield new IntroTerm.Lambda(param, body);
      }
      case IntroTerm.Tuple tuple -> {
        var items = tuple.items().map(f);
        if (items.sameElements(tuple.items(), true)) yield tuple;
        yield new IntroTerm.Tuple(items);
      }
      case IntroTerm.New neu -> {
        var struct = f.apply(neu.struct());
        var fields = ImmutableMap.from(neu.params().view().map((k, v) -> Tuple.of(k, f.apply(v))));
        if (struct == neu.struct() && fields.valuesView().sameElements(neu.params().valuesView())) yield neu;
        yield new IntroTerm.New((CallTerm.Struct) struct, fields);
      }
      case ElimTerm.App app -> {
        var function = f.apply(app.of());
        var arg = app.arg().descent(f);
        if (function == app.of() && arg == app.arg()) yield app;
        yield CallTerm.make(function, arg);
      }
      case ElimTerm.Proj proj -> {
        var tuple = f.apply(proj.of());
        if (tuple == proj.of()) yield proj;
        yield new ElimTerm.Proj(tuple, proj.ix());
      }
      case CallTerm.Struct struct -> {
        var args = struct.args().map(arg -> arg.descent(f));
        if (args.sameElements(struct.args(), true)) yield struct;
        yield new CallTerm.Struct(struct.ref(), struct.ulift(), args);
      }
      case CallTerm.Data data -> {
        var args = data.args().map(arg -> arg.descent(f));
        if (args.sameElements(data.args(), true)) yield data;
        yield new CallTerm.Data(data.ref(), data.ulift(), args);
      }
      case CallTerm.Con con -> {
        var head = con.head().descent(f);
        var args = con.conArgs().map(arg -> arg.descent(f));
        if (head == con.head() && args.sameElements(con.conArgs(), true)) yield con;
        yield new CallTerm.Con(head, args);
      }
      case CallTerm.Fn fn -> {
        var args = fn.args().map(arg -> arg.descent(f));
        if (args.sameElements(fn.args(), true)) yield fn;
        yield new CallTerm.Fn(fn.ref(), fn.ulift(), args);
      }
      case CallTerm.Access access -> {
        var struct = f.apply(access.of());
        var structArgs = access.structArgs().map(arg -> arg.descent(f));
        var fieldArgs = access.fieldArgs().map(arg -> arg.descent(f));
        if (struct == access.of()
          && structArgs.sameElements(access.structArgs(), true)
          && fieldArgs.sameElements(access.fieldArgs(), true))
          yield access;
        yield new CallTerm.Access(struct, access.ref(), structArgs, fieldArgs);
      }
      case CallTerm.Prim prim -> {
        var args = prim.args().map(arg -> arg.descent(f));
        yield switch (prim.id()) {
          case IMIN -> PrimTerm.Mula.and(args.first().term(), args.last().term());
          case IMAX -> PrimTerm.Mula.or(args.first().term(), args.last().term());
          case INVOL -> PrimTerm.Mula.inv(args.first().term());
          default -> {
            if (args.sameElements(prim.args(), true)) yield prim;
            yield new CallTerm.Prim(prim.ref(), prim.ulift(), args);
          }
        };
      }
      case CallTerm.Hole hole -> {
        var contextArgs = hole.contextArgs().map(arg -> arg.descent(f));
        var args = hole.args().map(arg -> arg.descent(f));
        if (contextArgs.sameElements(hole.contextArgs(), true) && args.sameElements(hole.args(), true)) yield hole;
        yield new CallTerm.Hole(hole.ref(), hole.ulift(), contextArgs, args);
      }
      case LitTerm.ShapedInt shaped -> {
        var type = f.apply(shaped.type());
        if (type == shaped.type()) yield shaped;
        yield new LitTerm.ShapedInt(shaped.repr(), shaped.shape(), type);
      }
      case FormTerm.PartTy ty -> {
        var type = f.apply(ty.type());
        var restr = ty.restr().fmap(f);
        if (type == ty.type() && restr == ty.restr()) yield ty;
        yield new FormTerm.PartTy(type, restr);
      }
      case IntroTerm.SadPartEl el -> {
        var u = f.apply(el.u());
        if (u == el.u()) yield el;
        yield new IntroTerm.SadPartEl(u);
      }
      case IntroTerm.HappyPartEl el -> {
        var type = f.apply(el.rhsType());
        var clauses = el.clauses().map(c -> c.rename(f));
        if (type == el.rhsType() && clauses.sameElements(el.clauses(), true)) yield el;
        yield new IntroTerm.HappyPartEl(clauses, type);
      }
      case FormTerm.Path path -> {
        var cube = path.cube().map(f);
        if (cube == path.cube()) yield path;
        yield new FormTerm.Path(cube);
      }
      case IntroTerm.PathLam lam -> {
        var body = f.apply(lam.body());
        if (body == lam.body()) yield lam;
        yield new IntroTerm.PathLam(lam.params(), body);
      }
      case RefTerm ref -> ref;
      case RefTerm.MetaPat metaPat -> metaPat;
      case RefTerm.Field field -> field;
      case ErrorTerm error -> error;
    };
  }

  default @NotNull Term subst(@NotNull Var var, @NotNull Term term) {
    return subst(new Subst(var, term));
  }

  default @NotNull Term subst(@NotNull Subst subst) {
    return new EndoFunctor.Substituter(subst).apply(this);
  }

  default @NotNull Term subst(@NotNull Map<Var, ? extends Term> subst) {
    return subst(new Subst(MutableMap.from(subst)));
  }

  default @NotNull Term subst(@NotNull Subst subst, int ulift) {
    return this.subst(subst).lift(ulift);
  }

  default @NotNull Term rename() {
    return new EndoFunctor.Renamer().apply(this);
  }

  default int findUsages(@NotNull Var var) {
    return new MonoidalVarFolder.Usages(var).apply(this);
  }

  default VarConsumer.ScopeChecker scopeCheck(@NotNull ImmutableSeq<LocalVar> allowed) {
    var checker = new VarConsumer.ScopeChecker(allowed);
    checker.accept(this);
    assert checker.isCleared() : "The scope checker is not properly cleared up";
    return checker;
  }

  /**
   * @param state used for inlining the holes.
   *              Can be null only if we're absolutely sure that holes are frozen,
   *              like in the error messages.
   */
  default @NotNull Term normalize(@NotNull TyckState state, @NotNull NormalizeMode mode) {
    return switch (mode) {
      case NULL -> this;
      case NF -> new Expander.Normalizer(state).apply(this);
      case WHNF -> new Expander.WHNFer(state).apply(this);
    };
  }

  default @NotNull Term freezeHoles(@Nullable TyckState state) {
    return new EndoFunctor() {
      @Override public @NotNull Term pre(@NotNull Term term) {
        return term instanceof CallTerm.Hole hole && state != null
          ? state.metas().getOrDefault(hole.ref(), term)
          : term;
      }
    }.apply(this);
  }

  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return new CoreDistiller(options).term(BaseDistiller.Outer.Free, this);
  }
  default @NotNull Term lift(int ulift) {
    return new EndoFunctor.Elevator(ulift).apply(this);
  }
  default @NotNull Term computeType(@NotNull TyckState state, @NotNull LocalCtx ctx) {
    return new LittleTyper(state, ctx).term(this);
  }

  /**
   * @author re-xyr
   */
  record Param(
    @NotNull LocalVar ref,
    @NotNull Term type,
    boolean explicit
  ) implements Bind, ParamLike<Term> {
    public Param(@NotNull ParamLike<?> param, @NotNull Term type) {
      this(param.ref(), type, param.explicit());
    }

    public static @NotNull ImmutableSeq<@NotNull Param> fromBuffer(@NotNull MutableList<Tuple3<LocalVar, Boolean, Term>> buf) {
      return buf.view().map(tup -> new Param(tup._1, tup._3, tup._2)).toImmutableSeq();
    }

    public @NotNull Param descent(@NotNull Function<@NotNull Term, @NotNull Term> f) {
      var type = f.apply(type());
      if (type == type()) return this;
      return new Param(this, type);
    }

    @Contract(" -> new") public @NotNull Param implicitify() {
      return new Param(ref, type, false);
    }

    @Contract(" -> new") public @NotNull Param rename() {
      return new Param(renameVar(), type, explicit);
    }

    @Contract(" -> new") public @NotNull LocalVar renameVar() {
      return new LocalVar(ref.name(), ref.definition());
    }

    @Override @Contract(" -> new") public @NotNull Arg<@NotNull Term> toArg() {
      return new Arg<>(toTerm(), explicit);
    }

    @Contract(" -> new") public @NotNull RefTerm toTerm() {
      return new RefTerm(ref);
    }

    public @NotNull Pat toPat() {
      return new Pat.Bind(explicit, ref, type);
    }

    public @NotNull Param subst(@NotNull Var var, @NotNull Term term) {
      return subst(new Subst(var, term));
    }

    public @NotNull Param subst(@NotNull Subst subst) {
      return subst(subst, 0);
    }

    public static @NotNull ImmutableSeq<Param> subst(
      @NotNull ImmutableSeq<@NotNull Param> params,
      @NotNull Subst subst, int ulift
    ) {
      return params.map(param -> param.subst(subst, ulift));
    }

    public static @NotNull ImmutableSeq<Param>
    subst(@NotNull ImmutableSeq<@NotNull Param> params, int ulift) {
      return subst(params, Subst.EMPTY, ulift);
    }

    public @NotNull Param subst(@NotNull Subst subst, int ulift) {
      return new Param(ref, type.subst(subst, ulift), explicit);
    }
  }
}

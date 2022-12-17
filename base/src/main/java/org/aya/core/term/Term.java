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
import org.aya.pretty.BasePrettier;
import org.aya.pretty.CorePrettier;
import org.aya.generic.AyaDocile;
import org.aya.generic.ParamLike;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Restr;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.LittleTyper;
import org.aya.tyck.TyckState;
import org.aya.tyck.env.LocalCtx;
import org.aya.util.Arg;
import org.aya.util.pretty.PrettierOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A well-typed and terminating term.
 *
 * @author ice1000
 */
public sealed interface Term extends AyaDocile, Restr.TermLike<Term>
  permits Callable, CoeTerm, Elimination, Formation, FormulaTerm, HCompTerm, InOutTerm, MatchTerm, MetaLitTerm, MetaPatTerm, PartialTerm, RefTerm, RefTerm.Field, StableWHNF {
  default @NotNull Term descent(@NotNull UnaryOperator<@NotNull Term> f) {
    return switch (this) {
      case PiTerm pi -> {
        var param = pi.param().descent(f);
        var body = f.apply(pi.body());
        if (param == pi.param() && body == pi.body()) yield pi;
        yield new PiTerm(param, body);
      }
      case SigmaTerm sigma -> {
        var params = sigma.params().map(param -> param.descent(f));
        if (params.sameElements(sigma.params(), true)) yield sigma;
        yield new SigmaTerm(params);
      }
      case SortTerm univ -> univ;
      case IntervalTerm interval -> interval;
      case FormulaTerm(var mula) -> {
        var formula = mula.fmap(f);
        if (formula == mula) yield this;
        yield new FormulaTerm(formula);
      }
      case StringTerm str -> str;
      case LamTerm lambda -> {
        var param = lambda.param().descent(f);
        var body = f.apply(lambda.body());
        if (param == lambda.param() && body == lambda.body()) yield lambda;
        yield new LamTerm(param, body);
      }
      case TupTerm tuple -> {
        var items = tuple.items().map(x -> x.descent(f));
        if (items.sameElements(tuple.items(), true)) yield tuple;
        yield new TupTerm(items);
      }
      case NewTerm neu -> {
        var struct = f.apply(neu.struct());
        var fields = ImmutableMap.from(neu.params().view().map((k, v) -> Tuple.of(k, f.apply(v))));
        if (struct == neu.struct() && fields.valuesView().sameElements(neu.params().valuesView(), true)) yield neu;
        yield new NewTerm((StructCall) struct, fields);
      }
      case AppTerm app -> {
        var function = f.apply(app.of());
        var arg = app.arg().descent(f);
        if (function == app.of() && arg == app.arg()) yield app;
        yield AppTerm.make(function, arg);
      }
      case ProjTerm proj -> {
        var tuple = f.apply(proj.of());
        if (tuple == proj.of()) yield proj;
        yield new ProjTerm(tuple, proj.ix());
      }
      case MatchTerm match -> {
        var discriminant = match.discriminant().map(f);
        var clauses = match.clauses().map(c -> c.descent(f));
        if (match.discriminant().sameElements(discriminant, true) && match.clauses().sameElements(clauses, true))
          yield match;
        yield new MatchTerm(discriminant, clauses);
      }
      case StructCall struct -> {
        var args = struct.args().map(arg -> arg.descent(f));
        if (args.sameElements(struct.args(), true)) yield struct;
        yield new StructCall(struct.ref(), struct.ulift(), args);
      }
      case DataCall data -> {
        var args = data.args().map(arg -> arg.descent(f));
        if (args.sameElements(data.args(), true)) yield data;
        yield new DataCall(data.ref(), data.ulift(), args);
      }
      case ConCall(var head0, var args0) -> {
        var head = head0.descent(f);
        var args = args0.map(arg -> arg.descent(f));
        if (head == head0 && args.sameElements(args0, true)) yield this;
        yield new ConCall(head, args);
      }
      case FnCall fn -> {
        var args = fn.args().map(arg -> arg.descent(f));
        if (args.sameElements(fn.args(), true)) yield fn;
        yield new FnCall(fn.ref(), fn.ulift(), args);
      }
      case FieldTerm access -> {
        var struct = f.apply(access.of());
        var structArgs = access.structArgs().map(arg -> arg.descent(f));
        var fieldArgs = access.fieldArgs().map(arg -> arg.descent(f));
        if (struct == access.of()
          && structArgs.sameElements(access.structArgs(), true)
          && fieldArgs.sameElements(access.fieldArgs(), true))
          yield access;
        yield new FieldTerm(struct, access.ref(), structArgs, fieldArgs);
      }
      case PrimCall prim -> {
        var args = prim.args().map(arg -> arg.descent(f));
        if (args.sameElements(prim.args(), true)) yield prim;
        yield new PrimCall(prim.ref(), prim.ulift(), args);
      }
      case MetaTerm hole -> {
        var contextArgs = hole.contextArgs().map(arg -> arg.descent(f));
        var args = hole.args().map(arg -> arg.descent(f));
        if (contextArgs.sameElements(hole.contextArgs(), true) && args.sameElements(hole.args(), true)) yield hole;
        yield new MetaTerm(hole.ref(), contextArgs, args);
      }
      case IntegerTerm shaped -> {
        var type = f.apply(shaped.type());
        if (type == shaped.type()) yield shaped;
        yield new IntegerTerm(shaped.repr(), shaped.recognition(), (DataCall) type);
      }
      case ListTerm shaped -> {
        var type = f.apply(shaped.type());
        var elements = shaped.repr().map(f);
        if (type == shaped.type() && elements.sameElements(shaped.repr(), true)) yield shaped;
        yield new ListTerm(elements, shaped.recognition(), (DataCall) type);
      }
      case MetaLitTerm lit -> {
        var type = f.apply(lit.type());
        if (type == lit.type()) yield lit;
        yield new MetaLitTerm(lit.sourcePos(), lit.repr(), lit.candidates(), type);
      }
      case PartialTyTerm ty -> {
        var type = f.apply(ty.type());
        var restr = ty.restr().map(f);
        if (type == ty.type() && restr == ty.restr()) yield ty;
        yield new PartialTyTerm(type, restr);
      }
      case PartialTerm el -> {
        var partial = el.partial().map(f);
        var rhs = f.apply(el.rhsType());
        if (partial == el.partial() && rhs == el.rhsType()) yield el;
        yield new PartialTerm(partial, rhs);
      }
      case PathTerm path -> path.map(f);
      case PLamTerm(var params, var body) lam -> {
        var newBody = f.apply(body);
        if (newBody == body) yield lam;
        yield new PLamTerm(params, newBody);
      }
      case PAppTerm(var of, var args, var cube) app -> {
        var newOf = f.apply(of);
        var refs = args.map(a -> a.descent(f));
        var newCube = cube.map(f);
        if (newOf == of && newCube == cube && refs.sameElements(args, true))
          yield app;
        yield new PAppTerm(newOf, refs, newCube);
      }
      case CoeTerm coe -> {
        var type = f.apply(coe.type());
        var restr = coe.restr().map(f);
        if (type == coe.type() && restr == coe.restr()) yield coe;
        yield new CoeTerm(type, AyaRestrSimplifier.INSTANCE.normalizeRestr(restr));
      }
      case RefTerm ref -> ref;
      case MetaPatTerm metaPat -> metaPat;
      case RefTerm.Field field -> field;
      case ErrorTerm error -> error;
      case HCompTerm hComp -> throw new UnsupportedOperationException("TODO");
      case InOutTerm inOutTerm -> {
        var phi = f.apply(inOutTerm.phi());
        var u = f.apply(inOutTerm.u());
        if (phi == inOutTerm.phi() && u == inOutTerm.u()) yield inOutTerm;
        yield InOutTerm.make(phi, u, inOutTerm.kind());
      }
    };
  }

  default @NotNull Term subst(@NotNull AnyVar var, @NotNull Term term) {
    return subst(new Subst(var, term));
  }

  default @NotNull Term subst(@NotNull Subst subst) {
    return new EndoTerm.Substituter(subst).apply(this);
  }

  default @NotNull Term subst(@NotNull Map<AnyVar, ? extends Term> subst) {
    return subst(new Subst(MutableMap.from(subst)));
  }

  default @NotNull Term subst(@NotNull Subst subst, int ulift) {
    return this.subst(subst).lift(ulift);
  }

  default @NotNull Term rename() {
    return new EndoTerm.Renamer().apply(this);
  }

  default int findUsages(@NotNull AnyVar var) {
    return new TermFolder.Usages(var).apply(this);
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
    return new EndoTerm() {
      @Override public @NotNull Term pre(@NotNull Term term) {
        return term instanceof MetaTerm hole && state != null
          ? state.metas().getOption(hole.ref()).map(this::pre).getOrDefault(term)
          : term;
      }
    }.apply(this);
  }

  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new CorePrettier(options).term(BasePrettier.Outer.Free, this);
  }
  default @NotNull Term lift(int ulift) {
    return new EndoTerm.Elevator(ulift).apply(this);
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
  ) implements ParamLike<Term> {
    public Param(@NotNull ParamLike<?> param, @NotNull Term type) {
      this(param.ref(), type, param.explicit());
    }

    public static @NotNull ImmutableSeq<@NotNull Param> fromBuffer(@NotNull MutableList<Tuple3<LocalVar, Boolean, Term>> buf) {
      return buf.view().map(tup -> new Param(tup._1, tup._3, tup._2)).toImmutableSeq();
    }

    public @NotNull Param descent(@NotNull UnaryOperator<@NotNull Term> f) {
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
      return ref.rename();
    }

    @Contract(" -> new") public @NotNull Arg<@NotNull Term> toArg() {
      return new Arg<>(toTerm(), explicit);
    }

    @Contract(" -> new") public @NotNull RefTerm toTerm() {
      return new RefTerm(ref);
    }

    public @NotNull Arg<Pat> toPat() {
      return new Arg<>(new Pat.Bind(ref, type), explicit);
    }

    public @NotNull Param subst(@NotNull AnyVar var, @NotNull Term term) {
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

    public static @NotNull Param interval(@NotNull LocalVar i) {
      return new Param(i, IntervalTerm.INSTANCE, true);
    }
  }

  record Matching(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Arg<Pat>> patterns,
    @NotNull Term body
  ) implements AyaDocile {
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Pat.Preclause.weaken(this).toDoc(options);
    }

    public @NotNull Matching descent(@NotNull Function<@NotNull Term, @NotNull Term> f) {
      var body = f.apply(body());
      if (body == body()) return this;
      return new Matching(sourcePos, patterns, body);
    }
  }
}

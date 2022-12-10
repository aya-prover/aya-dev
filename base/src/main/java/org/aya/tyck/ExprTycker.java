// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableTreeSet;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Tuple3;
import kala.value.LazyValue;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.*;
import org.aya.core.repr.AyaShape;
import org.aya.core.term.*;
import org.aya.core.visitor.AyaRestrSimplifier;
import org.aya.core.visitor.DeltaExpander;
import org.aya.core.visitor.Subst;
import org.aya.core.visitor.Zonker;
import org.aya.distill.AyaDistillerOptions;
import org.aya.generic.AyaDocile;
import org.aya.generic.Constants;
import org.aya.generic.Modifier;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.CofThy;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.env.MapLocalCtx;
import org.aya.tyck.error.*;
import org.aya.tyck.pat.PatTycker;
import org.aya.tyck.pat.TypedSubst;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.Unifier;
import org.aya.util.Arg;
import org.aya.util.Ordering;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * @apiNote make sure to instantiate this class once for each {@link Decl.TopLevel}.
 * Do <em>not</em> use multiple instances in the tycking of one {@link Decl.TopLevel}
 * and do <em>not</em> reuse instances of this class in the tycking of multiple {@link Decl.TopLevel}s.
 */
public final class ExprTycker extends Tycker {
  public @NotNull LocalCtx localCtx = new MapLocalCtx();

  /**
   * a `let` sequence, consider we are tycking in
   * {@code let ... in HERE}
   */
  public @NotNull TypedSubst lets = new TypedSubst();
  public final @NotNull AyaShape.Factory shapeFactory;
  public final @NotNull MutableTreeSet<Expr.WithTerm> withTerms =
    MutableTreeSet.create(Comparator.comparing(SourceNode::sourcePos));

  @Override public void solveMetas() {
    super.solveMetas();
    withTerms.forEach(w -> w.theCore().update(r -> r.freezeHoles(state)));
  }

  public boolean inProp = false;

  public <T> T withInProp(boolean inProp, @NotNull Supplier<T> supplier) {
    var origin = this.inProp;
    this.inProp = inProp;
    try {
      return supplier.get();
    } finally {
      this.inProp = origin;
    }
  }

  public <T> T withSubSubst(Supplier<T> supplier) {
    var oldLets = lets;
    lets = oldLets.derive();
    var result = supplier.get();
    lets = oldLets;
    return result;
  }

  public <T> T withResult(@NotNull Term result, @NotNull Supplier<T> supplier) {
    return withInProp(isPropType(result), supplier);
  }

  private @NotNull Result doSynthesize(@NotNull Expr expr) {
    return switch (expr) {
      case Expr.Lambda lam when lam.param().type() instanceof Expr.Hole -> inherit(lam, generatePi(lam));
      case Expr.Lambda lam -> {
        var paramTy = ty(lam.param().type()).wellTyped;
        yield localCtx.with(lam.param().ref(), paramTy, () -> {
          var body = synthesize(lam.body());
          var param = new Term.Param(lam.param(), paramTy);
          var pi = new PiTerm(param, body.type()).freezeHoles(state).rename();
          return new TermResult(new LamTerm(param, body.wellTyped()), pi);
        });
      }
      case Expr.Sort sort -> ty(sort);
      case Expr.Ref ref -> switch (ref.resolvedVar()) {
        case LocalVar loc -> lets.getOption(loc).getOrElse(() -> {
          // not defined in lets, search localCtx
          var ty = localCtx.get(loc);
          return new TermResult(new RefTerm(loc), ty);
        });
        case DefVar<?, ?> defVar -> inferRef(defVar);
        default -> throw new InternalException("Unknown var: " + ref.resolvedVar().getClass());
      };
      case Expr.Pi pi -> ty(pi);
      case Expr.Sigma sigma -> ty(sigma);
      case Expr.Lift lift -> {
        var result = synthesize(lift.expr());
        var levels = lift.lift();
        yield new TermResult(result.wellTyped().lift(levels), result.type().lift(levels));
      }
      case Expr.New aNew -> {
        var structExpr = aNew.struct();
        var struct = instImplicits(synthesize(structExpr).wellTyped(), structExpr.sourcePos());
        if (!(struct instanceof StructCall structCall))
          yield fail(structExpr, struct, BadTypeError.structCon(state, aNew, struct));
        var structRef = structCall.ref();
        var subst = new Subst(Def.defTele(structRef).map(Term.Param::ref), structCall.args().map(Arg::term));

        var fields = MutableList.<Tuple2<DefVar<FieldDef, TeleDecl.StructField>, Term>>create();
        var missing = MutableList.<AnyVar>create();
        var conFields = MutableMap.from(aNew.fields().view().map(t -> Tuple.of(t.name().data(), t)));

        for (var defField : structRef.core.fields) {
          var fieldRef = defField.ref();
          var conFieldOpt = conFields.remove(fieldRef.name());
          if (conFieldOpt.isEmpty()) {
            if (defField.body.isEmpty())
              missing.append(fieldRef); // no value available, skip and prepare error reporting
            else {
              // use default value from defField
              var field = defField.body.get().subst(subst, structCall.ulift());
              fields.append(Tuple.of(fieldRef, field));
              subst.add(fieldRef, field);
            }
            continue;
          }
          var conField = conFieldOpt.get();
          conField.resolvedField().set(fieldRef);
          var type = Def.defType(fieldRef).subst(subst, structCall.ulift());
          var telescope = Term.Param.subst(fieldRef.core.selfTele, subst, structCall.ulift());
          var bindings = conField.bindings();
          if (telescope.sizeLessThan(bindings.size())) {
            // TODO: Maybe it's better for field to have a SourcePos?
            yield fail(aNew, structCall, new FieldError.ArgMismatch(aNew.sourcePos(), defField, bindings.size()));
          }
          var fieldExpr = bindings.zipView(telescope).foldRight(conField.body(), (pair, lamExpr) -> new Expr.Lambda(conField.body().sourcePos(), new Expr.Param(pair._1.sourcePos(), pair._1.data(), pair._2.explicit()), lamExpr));
          var field = inherit(fieldExpr, type).wellTyped();
          fields.append(Tuple.of(fieldRef, field));
          subst.add(fieldRef, field);
        }

        if (missing.isNotEmpty())
          yield fail(aNew, structCall, new FieldError.MissingField(aNew.sourcePos(), missing.toImmutableSeq()));
        if (conFields.isNotEmpty())
          yield fail(aNew, structCall, new FieldError.NoSuchField(aNew.sourcePos(), conFields.keysView().toImmutableSeq()));
        yield new TermResult(new NewTerm(structCall, ImmutableMap.from(fields)), structCall);
      }
      case Expr.Proj proj -> {
        var struct = proj.tup();
        var projectee = instImplicits(synthesize(struct), struct.sourcePos());
        yield proj.ix().fold(ix -> {
          if (!(projectee.type() instanceof SigmaTerm(var telescope)))
            return fail(struct, projectee.type(), BadTypeError.sigmaAcc(state, struct, ix, projectee.type()));
          var projecteeIsProp = isPropType(projectee.type());
          if (!inProp && projecteeIsProp)
            return fail(struct, projectee.type(), BadTypeError.projProp(state, struct, ix, projectee.type()));
          var index = ix - 1;
          if (index < 0 || index >= telescope.size())
            return fail(proj, new TupleError.ProjIxError(proj, ix, telescope.size()));
          var type = telescope.get(index).type();
          var subst = ProjTerm.projSubst(projectee.wellTyped(), index, telescope);
          return new TermResult(ProjTerm.proj(projectee.wellTyped(), ix), type.subst(subst));
        }, sp -> {
          var fieldName = sp.justName();
          if (!(projectee.type() instanceof StructCall structCall))
            return fail(struct, ErrorTerm.unexpected(projectee.type()), BadTypeError.structAcc(state, struct, fieldName, projectee.type()));
          // TODO[ice]: instantiate the type
          if (!(proj.resolvedVar() instanceof DefVar<?, ?> defVar && defVar.core instanceof FieldDef field))
            return fail(proj, new FieldError.UnknownField(sp.sourcePos(), fieldName));
          var fieldRef = field.ref();

          var structIsProp = fieldRef.core.inProp();
          if (!inProp && structIsProp)
            return fail(proj, BadTypeError.projPropStruct(state, struct, fieldName, projectee.type()));

          var structSubst = DeltaExpander.buildSubst(Def.defTele(structCall.ref()), structCall.args());
          var tele = Term.Param.subst(fieldRef.core.selfTele, structSubst, 0);
          var teleRenamed = tele.map(Term.Param::rename);
          var access = new FieldTerm(projectee.wellTyped(), fieldRef, structCall.args(), teleRenamed.map(Term.Param::toArg));
          return new TermResult(LamTerm.make(teleRenamed, access), PiTerm.make(tele, field.result().subst(structSubst)));
        });
      }
      case Expr.Tuple tuple -> {
        var items = tuple.items().map(this::synthesize);
        yield new TermResult(new TupTerm(items.map(Result::wellTyped)), new SigmaTerm(items.map(item -> new Term.Param(Constants.anonymous(), item.type(), true))));
      }
      case Expr.Coe coe -> {
        var defVar = coe.resolvedVar();
        assert defVar instanceof DefVar<?, ?> res
          && res.core instanceof PrimDef def && PrimDef.ID.projSyntax(def.id) : "desugar bug";
        var mockApp = Expr.app(
          new Expr.Ref(coe.id().sourcePos(), defVar), Seq.of(
            new WithPos<>(coe.sourcePos(), new Expr.NamedArg(true, coe.type())),
            new WithPos<>(coe.sourcePos(), new Expr.NamedArg(true, coe.restr()))
          ).view());
        var res = synthesize(mockApp);
        if (whnf(res.wellTyped()) instanceof CoeTerm(var type, var restr) && !(type instanceof ErrorTerm)) {
          var bad = new Object() {
            Term typeSubst;
            boolean stuck = false;
          };
          var freezes = CofThy.conv(restr, new Subst(), subst -> {
            // normalizes to NF in case the `type` was eta-expanded from a definition.
            var typeSubst = type.subst(subst).normalize(state, NormalizeMode.NF);
            // ^ `typeSubst` should now be instantiated under cofibration `restr`, and
            // it must be the form of `(i : I) -> A`. We need to ensure the `i` does not occur in `A` at all.
            // See also: https://github.com/ice1000/guest0x0/blob/main/base/src/main/java/org/aya/guest0x0/tyck/Elaborator.java#L293-L310
            IntPredicate post = usages -> {
              if (usages != 0) bad.typeSubst = typeSubst;
              return usages == 0;
            };
            return switch (typeSubst) {
              case LamTerm(var param, var body) -> post.test(body.findUsages(param.ref()));
              case PLamTerm(var params, var body) -> post.test(body.findUsages(params.first()));
              default -> {
                bad.stuck = true;
                yield false;
              }
            };
          });
          if (!freezes) yield fail(coe, new CubicalError.CoeVaryingType(
            coe.type(), type, bad.typeSubst, restr, bad.stuck));
        }
        yield res;
      }
      case Expr.App(var sourcePos, var appF, var argument) -> {
        var f = synthesize(appF);
        if (f.wellTyped() instanceof ErrorTerm || f.type() instanceof ErrorTerm) yield f;
        var app = f.wellTyped();
        var fTy = whnf(f.type());
        var argLicit = argument.explicit();
        if (fTy instanceof MetaTerm fTyHole) {
          // [ice] Cannot 'generatePi' because 'generatePi' takes the current contextTele,
          // but it may contain variables absent from the 'contextTele' of 'fTyHole.ref.core'
          var pi = fTyHole.asPi(argLicit);
          unifier(sourcePos, Ordering.Eq).compare(fTy, pi, null);
          fTy = whnf(fTy);
        }
        PathTerm cube;
        PiTerm pi;
        var subst = new Subst();
        try {
          var tup = ensurePiOrPath(fTy);
          pi = tup._1;
          cube = tup._2;
          while (pi.param().explicit() != argLicit || argument.name() != null && !Objects.equals(pi.param().ref().name(), argument.name())) {
            if (argLicit || argument.name() != null) {
              // that implies paramLicit == false
              var holeApp = mockArg(pi.param().subst(subst), argument.term().sourcePos());
              // path types are always explicit
              app = AppTerm.make(app, holeApp);
              subst.addDirectly(pi.param().ref(), holeApp.term());
              tup = ensurePiOrPath(pi.body());
              pi = tup._1;
              if (tup._2 != null) cube = tup._2;
            } else yield fail(expr, new ErrorTerm(pi.body()), new LicitError.UnexpectedImplicitArg(argument));
          }
          tup = ensurePiOrPath(pi.subst(subst));
          pi = tup._1;
          if (tup._2 != null) cube = tup._2;
        } catch (NotPi notPi) {
          yield fail(expr, ErrorTerm.unexpected(notPi.what), BadTypeError.pi(state, expr, notPi.what));
        }
        if (appF instanceof Expr.WithTerm withTerm) {
          addWithTerm(withTerm, new TermResult(app, pi));
        }
        var elabArg = inherit(argument.term(), pi.param().type()).wellTyped();
        subst.addDirectly(pi.param().ref(), elabArg);
        var arg = new Arg<>(elabArg, argLicit);
        var newApp = cube == null
          ? AppTerm.make(app, arg)
          : cube.makeApp(app, arg).subst(subst);
        // ^ instantiate inserted implicits to the partial element in `Cube`.
        // It is better to `cube.subst().makeApp()`, but we don't have a `subst` method for `Cube`.
        // Anyway, the `Term.descent` will recurse into the `Cube` for `PathApp` and substitute the partial element.
        yield new TermResult(newApp, pi.body().subst(subst));
      }
      case Expr.Hole hole -> inherit(hole, localCtx.freshHole(null, Constants.randomName(hole), hole.sourcePos())._2);
      case Expr.Error err -> TermResult.error(err.description());
      case Expr.LitInt lit -> {
        int integer = lit.integer();
        // TODO[literal]: int literals. Currently the parser does not allow negative literals.
        var defs = shapeFactory.findImpl(AyaShape.NAT_SHAPE);
        if (defs.isEmpty()) yield fail(expr, new NoRuleError(expr, null));
        if (defs.sizeGreaterThan(1)) {
          var type = localCtx.freshHole(null, "_ty" + lit.integer() + "'", lit.sourcePos());
          yield new TermResult(new MetaLitTerm(lit.sourcePos(), lit.integer(), defs, type._1), type._1);
        }
        var match = defs.first();
        var type = new DataCall(((DataDef) match._1).ref, 0, ImmutableSeq.empty());
        yield new TermResult(new IntegerTerm(integer, match._2, type), type);
      }
      case Expr.LitString litStr -> {
        if (!state.primFactory().have(PrimDef.ID.STRING))
          yield fail(expr, new NoRuleError(expr, null));

        yield new TermResult(new StringTerm(litStr.string()), state.primFactory().getCall(PrimDef.ID.STRING));
      }
      case Expr.Path path -> localCtx.withIntervals(path.params().view(), () -> {
        var type = synthesize(path.type());
        var partial = elaboratePartial(path.partial(), type.wellTyped());
        var cube = new PathTerm(path.params(), type.wellTyped(), partial);
        return new TermResult(cube, type.type());
      });
      case Expr.Array arr when arr.arrayBlock().isRight() -> {
        var arrayBlock = arr.arrayBlock().getRightValue();
        var elements = arrayBlock.exprList();

        // find def
        var defs = shapeFactory.findImpl(AyaShape.LIST_SHAPE);
        if (defs.isEmpty()) yield fail(expr, new NoRuleError(expr, null));
        // TODO: can we proceed with ambiguity with MetaLitTerm? see literal-ambiguous-3.aya
        if (defs.sizeGreaterThan(1)) yield fail(expr, new Zonker.UnsolvedLit(new MetaLitTerm(
          arr.sourcePos(), arr, defs, ErrorTerm.typeOf(arr))));

        var match = defs.first();
        var def = (DataDef) match._1;

        // preparing
        var dataParam = Def.defTele(def.ref).first();
        var sort = dataParam.type();    // the sort of type below.
        var hole = localCtx.freshHole(sort, arr.sourcePos());
        var type = new DataCall(def.ref(), 0, ImmutableSeq.of(
          new Arg<>(hole._1, dataParam.explicit())));

        // do type check
        var results = elements.map(element -> inherit(element, hole._1).wellTyped());
        yield new TermResult(new ListTerm(results, match._2, type), type);
      }
      case Expr.Let let -> {
        // pushing telescopes into lambda params, for example:
        // `let f (x : A) : B x` is desugared to `let f : Pi (x : A) -> B x`
        var letBind = let.bind();
        var typeExpr = Expr.buildPi(letBind.sourcePos(),
          letBind.telescope().view(), letBind.result());
        // as well as the body of the binding, for example:
        // `let f x := g` is desugared to `let f := \x => g`
        var definedAsExpr = Expr.buildLam(letBind.sourcePos(),
          letBind.telescope().view(), letBind.definedAs());

        // Now everything is in the form of `let f : G := g in h`

        // See the TeleDecl.FnDecl case of StmtTycker#tyckHeader
        var type = synthesize(typeExpr).wellTyped().freezeHoles(state);
        var definedAsResult = inherit(definedAsExpr, type);
        var nameAndType = new Term.Param(let.bind().bindName(), definedAsResult.type(), true);

        var bodyResult = subscoped(() -> {
          localCtx.put(nameAndType);
          return synthesize(let.body());
        });

        // `let f : G := g in h` is desugared to `(\ (f : G) => h) g`

        // (\ (f : G) => h) : G -> {??}
        var lam = LamTerm.make(SeqView.of(nameAndType), bodyResult.wellTyped());

        // then apply a `g`
        // (\ (f : G) => h) g : {??}
        var full = AppTerm.make(lam, new Arg<>(definedAsResult.wellTyped(), true));

        yield new TermResult(full, bodyResult.type());
      }
      default -> fail(expr, new NoRuleError(expr, null));
    };
  }

  public @NotNull Restr<Term> restr(@NotNull Restr<Expr> restr) {
    return restr.mapCond(this::condition);
  }

  private @NotNull Restr.Cond<Term> condition(@NotNull Restr.Cond<Expr> c) {
    // forall i. (c_i is valid)
    return new Restr.Cond<>(inherit(c.inst(), IntervalTerm.INSTANCE).wellTyped(), c.isOne());
    // ^ note: `inst` may be ErrorTerm!
  }

  public @NotNull Partial<Term> elaboratePartial(@NotNull Expr.PartEl partial, @NotNull Term type) {
    var s = new ClauseTyckState();
    var sides = partial.clauses().flatMap(sys -> clause(sys._1, sys._2, type, s));
    confluence(sides, partial, type);
    if (s.isConstantFalse) return new Partial.Split<>(ImmutableSeq.empty());
    if (s.truthValue != null) return new Partial.Const<>(s.truthValue);
    return new Partial.Split<>(sides);
  }

  private void confluence(@NotNull ImmutableSeq<Restr.Side<Term>> clauses, @NotNull Expr loc, @NotNull Term type) {
    for (int i = 1; i < clauses.size(); i++) {
      var lhs = clauses.get(i);
      for (int j = 0; j < i; j++) {
        var rhs = clauses.get(j);
        CofThy.conv(lhs.cof().and(rhs.cof()), new Subst(), subst -> boundary(loc, lhs.u(), rhs.u(), type, subst));
      }
    }
  }

  private boolean boundary(@NotNull Expr loc, @NotNull Term lhs, @NotNull Term rhs, @NotNull Term type, Subst subst) {
    var l = whnf(lhs.subst(subst));
    var r = whnf(rhs.subst(subst));
    var t = whnf(type.subst(subst));
    var unifier = unifier(loc.sourcePos(), Ordering.Eq);
    var happy = unifier.compare(l, r, t);
    if (!happy) reporter.report(new CubicalError.BoundaryDisagree(loc, lhs, rhs, unifier.getFailure(), state));
    return happy;
  }

  private static class ClauseTyckState {
    public boolean isConstantFalse = false;
    public @Nullable Term truthValue;
  }

  private @NotNull SeqView<Restr.Side<Term>> clause(@NotNull Expr lhs, @NotNull Expr rhs, @NotNull Term rhsType, @NotNull ClauseTyckState clauseState) {
    return switch (AyaRestrSimplifier.INSTANCE.isOne(whnf(inherit(lhs, IntervalTerm.INSTANCE).wellTyped()))) {
      case Restr.Disj<Term> restr -> {
        var list = MutableList.<Restr.Side<Term>>create();
        for (var cof : restr.orz()) {
          var u = CofThy.vdash(cof, new Subst(), subst -> inherit(rhs, whnf(rhsType.subst(subst))).wellTyped());
          if (u.isDefined()) {
            if (u.get() == null) {
              // ^ some `inst` in `cofib.ands()` are ErrorTerms, or we have bugs.
              // Q: report error again?
              yield SeqView.empty();
            } else {
              list.append(new Restr.Side<>(cof, u.get()));
            }
          }
        }
        yield list.view();
      }
      case Restr.Const<Term> c -> {
        if (c.isOne()) clauseState.truthValue = inherit(rhs, rhsType).wellTyped();
        else clauseState.isConstantFalse = true;
        yield SeqView.empty();
      }
    };
  }

  private Term instImplicits(@NotNull Term term, @NotNull SourcePos pos) {
    term = whnf(term);
    while (term instanceof LamTerm intro && !intro.param().explicit()) {
      term = whnf(AppTerm.make(intro, mockArg(intro.param(), pos)));
    }
    return term;
  }

  private Result instImplicits(@NotNull Result result, @NotNull SourcePos pos) {
    var type = whnf(result.type());
    var term = result.wellTyped();
    while (type instanceof PiTerm pi && !pi.param().explicit()) {
      var holeApp = mockArg(pi.param(), pos);
      term = AppTerm.make(term, holeApp);
      type = whnf(pi.substBody(holeApp.term()));
    }
    return new TermResult(term, type);
  }

  private static final class NotPi extends Exception {
    private final @NotNull Term what;

    public NotPi(@NotNull Term what) {
      this.what = what;
    }
  }

  private Tuple2<PiTerm, @Nullable PathTerm>
  ensurePiOrPath(@NotNull Term term) throws NotPi {
    term = whnf(term);
    if (term instanceof PiTerm pi) return Tuple.of(pi, null);
    if (term instanceof PathTerm cube)
      return Tuple.of(cube.computePi(), cube);
    else throw new NotPi(term);
  }

  private @NotNull Result doInherit(@NotNull Expr expr, @NotNull Term term) {
    return switch (expr) {
      case Expr.Tuple(var pos, var it) -> {
        var items = MutableList.<Term>create();
        var resultTele = MutableList.<Term.@NotNull Param>create();
        var typeWHNF = whnf(term);
        if (typeWHNF instanceof MetaTerm hole) yield inheritFallbackUnify(hole, synthesize(expr), expr);
        if (!(typeWHNF instanceof SigmaTerm(var params)))
          yield fail(expr, term, BadTypeError.sigmaCon(state, expr, typeWHNF));
        var againstTele = params.view();
        var subst = new Subst(MutableMap.create());
        for (var iter = it.iterator(); iter.hasNext(); ) {
          var item = iter.next();
          var first = againstTele.first().subst(subst);
          var result = inherit(item, first.type());
          items.append(result.wellTyped());
          var ref = first.ref();
          resultTele.append(new Term.Param(ref, result.type(), first.explicit()));
          againstTele = againstTele.drop(1);
          if (againstTele.isNotEmpty())
            // LGTM! The show must go on
            subst.add(ref, result.wellTyped());
          else if (iter.hasNext())
            // Too many items
            yield fail(expr, term, new TupleError.ElemMismatchError(pos, params.size(), it.size()));
        }
        var resTy = new SigmaTerm(resultTele.toImmutableSeq());
        yield new TermResult(new TupTerm(items.toImmutableSeq()), resTy);
      }
      case Expr.Hole hole -> {
        // TODO[ice]: deal with unit type
        var freshHole = localCtx.freshHole(term, Constants.randomName(hole), hole.sourcePos());
        if (hole.explicit()) reporter.report(new Goal(state, freshHole._1, hole.accessibleLocal().get()));
        yield new TermResult(freshHole._2, term);
      }
      case Expr.Sort sortExpr -> {
        var result = ty(sortExpr);
        var normTerm = whnf(term);
        if (normTerm instanceof SortTerm sort) {
          var unifier = unifier(sortExpr.sourcePos(), Ordering.Lt);
          unifier.compareSort(result.type(), sort);
        } else {
          unifyTyReported(result.type(), normTerm, sortExpr);
        }
        yield new TermResult(result.wellTyped(), normTerm);
      }
      case Expr.Lambda lam -> {
        if (term instanceof MetaTerm) {
          if (lam.param().type() instanceof Expr.Hole)
            unifyTy(term, generatePi(lam), lam.param().sourcePos());
          else yield inheritFallbackUnify(term, synthesize(lam), lam);
        }
        yield switch (whnf(term)) {
          case PiTerm dt -> {
            var param = lam.param();
            if (param.explicit() != dt.param().explicit()) {
              yield fail(lam, dt, new LicitError.LicitMismatch(lam, dt));
            }
            var var = param.ref();
            var lamParam = param.type();
            var type = dt.param().type();
            var result = synthesize(lamParam).wellTyped();
            var comparison = unifyTy(result, type, lamParam.sourcePos());
            if (comparison != null) {
              // TODO: maybe also report this unification failure?
              yield fail(lam, dt, BadTypeError.lamParam(state, lam, type, result));
            } else type = result;
            addWithTerm(param, type);
            var resultParam = new Term.Param(var, type, param.explicit());
            var body = dt.substBody(resultParam.toTerm());
            yield localCtx.with(resultParam, () -> {
              var rec = check(lam.body(), body);
              return new TermResult(new LamTerm(resultParam, rec.wellTyped()), dt);
            });
          }
          // Path lambda!
          case PathTerm path -> checkBoundaries(expr, path, new Subst(),
            inherit(expr, path.computePi()).wellTyped());
          default -> fail(lam, term, BadTypeError.pi(state, lam, term));
        };
      }
      case Expr.LitInt(var pos, var end) -> {
        var ty = whnf(term);
        if (ty instanceof IntervalTerm) {
          if (end == 0 || end == 1) yield new TermResult(end == 0 ? FormulaTerm.LEFT : FormulaTerm.RIGHT, ty);
          else yield fail(expr, new PrimError.BadInterval(pos, end));
        }
        yield inheritFallbackUnify(term, synthesize(expr), expr);
      }
      case Expr.PartEl el -> {
        if (!(whnf(term) instanceof PartialTyTerm ty)) yield fail(el, term, BadTypeError.partTy(state, el, term));
        var cofTy = ty.restr();
        var rhsType = ty.type();
        var partial = elaboratePartial(el, rhsType);
        var face = partial.restr();
        if (!CofThy.conv(cofTy, new Subst(), subst -> CofThy.satisfied(subst.restr(state, face))))
          yield fail(el, new CubicalError.FaceMismatch(el, face, cofTy));
        yield new TermResult(new PartialTerm(partial, rhsType), ty);
      }
      case Expr.Match match -> {
        var discriminant = match.discriminant().map(this::synthesize);
        var sig = new Def.Signature<>(discriminant.map(r -> new Term.Param(new LocalVar("_"), r.type(), true)), term);
        var result = PatTycker.elabClausesClassified(this, match.clauses(), sig, match.sourcePos());
        yield new TermResult(new MatchTerm(discriminant.map(Result::wellTyped), result.matchings()), term);
      }
      default -> inheritFallbackUnify(term, synthesize(expr), expr);
    };
  }

  private TermResult checkBoundaries(Expr expr, PathTerm path, Subst subst, Term lambda) {
    var applied = path.applyDimsTo(lambda);
    return localCtx.withIntervals(path.params().view(), () -> {
      var happy = switch (path.partial()) {
        case Partial.Const<Term> sad -> boundary(expr, applied, sad.u(), path.type(), subst);
        case Partial.Split<Term> hap -> hap.clauses().allMatch(c ->
          CofThy.conv(c.cof(), subst, s -> boundary(expr, applied, c.u(), path.type(), s)));
      };
      return happy ? new TermResult(new PLamTerm(path.params(), applied), path)
        : new TermResult(ErrorTerm.unexpected(expr), path);
    });
  }

  private @NotNull TyResult doTy(@NotNull Expr expr) {
    return switch (expr) {
      case Expr.Hole hole -> {
        var freshHole = localCtx.freshHole(SortTerm.Type0, Constants.randomName(hole), hole.sourcePos());
        if (hole.explicit()) reporter.report(new Goal(state, freshHole._1, hole.accessibleLocal().get()));
        // TODO: implement type-only hole
        yield new TyResult(freshHole._2, SortTerm.Type0);
      }
      case Expr.Sort sort -> {
        var self = new SortTerm(sort.kind(), sort.lift());
        yield new TyResult(self, self.succ());
      }
      case Expr.Pi pi -> {
        var param = pi.param();
        final var var = param.ref();
        var domRes = ty(param.type());
        addWithTerm(param, domRes.wellTyped());
        var resultParam = new Term.Param(var, domRes.wellTyped(), param.explicit());
        yield localCtx.with(resultParam, () -> {
          var cod = ty(pi.last());
          return new TyResult(new PiTerm(resultParam, cod.wellTyped()), sortPi(pi, domRes.type(), cod.type()));
        });
      }
      case Expr.Sigma sigma -> {
        var resultTele = MutableList.<Tuple3<LocalVar, Boolean, Term>>create();
        var resultTypes = MutableList.<SortTerm>create();
        for (var tuple : sigma.params()) {
          var result = ty(tuple.type());
          addWithTerm(tuple, result.wellTyped());
          resultTypes.append(result.type());
          var ref = tuple.ref();
          localCtx.put(ref, result.wellTyped());
          resultTele.append(Tuple.of(ref, tuple.explicit(), result.wellTyped()));
        }
        var unifier = unifier(sigma.sourcePos(), Ordering.Lt);
        var maxSort = resultTypes.reduce(SigmaTerm::max);
        if (!maxSort.isProp()) resultTypes.forEach(t -> unifier.compareSort(t, maxSort));
        localCtx.remove(sigma.params().view().map(Expr.Param::ref));
        yield new TyResult(new SigmaTerm(Term.Param.fromBuffer(resultTele)), maxSort);
      }
      default -> {
        var result = synthesize(expr);
        yield new TyResult(result.wellTyped(), sort(expr, result.type()));
      }
    };
  }

  public @NotNull ExprTycker.SortResult sort(@NotNull Expr expr) {
    return new SortResult(sort(expr, ty(expr).wellTyped()));
  }

  private @NotNull SortTerm sort(@NotNull Expr errorMsg, @NotNull Term term) {
    return switch (whnf(term)) {
      case SortTerm u -> u;
      case MetaTerm hole -> {
        unifyTyReported(hole, SortTerm.Type0, errorMsg);
        yield SortTerm.Type0;
      }
      default -> {
        reporter.report(BadTypeError.univ(state, errorMsg, term));
        yield SortTerm.Type0;
      }
    };
  }

  private void traceExit(Result result, @NotNull Expr expr) {
    var frozen = LazyValue.of(() -> result.freezeHoles(state));
    tracing(builder -> {
      builder.append(new Trace.TyckT(frozen.get(), expr.sourcePos()));
      builder.reduce();
    });
    if (expr instanceof Expr.WithTerm withTerm) {
      addWithTerm(withTerm, frozen.get());
    }
  }

  public void addWithTerm(@NotNull Expr.WithTerm withTerm, @NotNull Result result) {
    withTerms.add(withTerm);
    withTerm.theCore().set(result);
  }

  public void addWithTerm(@NotNull Expr.Param param, @NotNull Term type) {
    addWithTerm(param, new TermResult(new RefTerm(param.ref()), type));
  }

  public ExprTycker(@NotNull PrimDef.Factory primFactory, @NotNull AyaShape.Factory shapeFactory, @NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder) {
    super(reporter, new TyckState(primFactory), traceBuilder);
    this.shapeFactory = shapeFactory;
  }

  public @NotNull Result inherit(@NotNull Expr expr, @NotNull Term type) {
    tracing(builder -> builder.shift(new Trace.ExprT(expr, type.freezeHoles(state))));
    Result result;
    if (type instanceof PiTerm pi && !pi.param().explicit() && needImplicitParamIns(expr)) {
      var implicitParam = new Term.Param(new LocalVar(Constants.ANONYMOUS_PREFIX), pi.param().type(), false);
      var body = localCtx.with(implicitParam, () -> inherit(expr, pi.substBody(implicitParam.toTerm()))).wellTyped();
      result = new TermResult(new LamTerm(implicitParam, body), pi);
    } else result = doInherit(expr, type);
    traceExit(result, expr);
    return result;
  }

  public @NotNull Result synthesize(@NotNull Expr expr) {
    tracing(builder -> builder.shift(new Trace.ExprT(expr, null)));
    var res = doSynthesize(expr);
    traceExit(res, expr);
    return res;
  }

  public @NotNull TyResult ty(@NotNull Expr expr) {
    tracing(builder -> builder.shift(new Trace.ExprT(expr, null)));
    var result = doTy(expr);
    traceExit(result, expr);
    return result;
  }

  public @NotNull SortTerm sortPi(@NotNull Expr expr, @NotNull SortTerm domain, @NotNull SortTerm codomain) {
    return sortPiImpl(new SortPiParam(reporter, expr), domain, codomain);
  }

  public static @NotNull SortTerm sortPi(@NotNull SortTerm domain, @NotNull SortTerm codomain) throws IllegalArgumentException {
    return sortPiImpl(null, domain, codomain);
  }

  private record SortPiParam(@NotNull Reporter reporter, @NotNull Expr expr) {
  }

  private static @NotNull SortTerm sortPiImpl(@Nullable SortPiParam p, @NotNull SortTerm domain, @NotNull SortTerm codomain) throws IllegalArgumentException {
    var result = PiTerm.max(domain, codomain);
    if (p == null) {
      assert result != null;
      return result;
    }
    if (result == null) {
      p.reporter.report(new SortPiError(p.expr.sourcePos(), domain, codomain));
      return SortTerm.Type0;
    } else {
      return result;
    }
  }

  private static boolean needImplicitParamIns(@NotNull Expr expr) {
    return expr instanceof Expr.Lambda ex && ex.param().explicit() || !(expr instanceof Expr.Lambda);
  }

  public @NotNull Result zonk(@NotNull Result result) {
    return new TermResult(zonk(result.wellTyped()), zonk(result.type()));
  }

  private @NotNull Term generatePi(Expr.@NotNull Lambda expr) {
    var param = expr.param();
    return generatePi(expr.sourcePos(), param.ref().name(), param.explicit());
  }

  private @NotNull Term generatePi(@NotNull SourcePos pos, @NotNull String name, boolean explicit) {
    var genName = name + Constants.GENERATED_POSTFIX;
    // [ice]: unsure if ZERO is good enough
    var domain = localCtx.freshHole(SortTerm.Type0, genName + "ty", pos)._2;
    var codomain = localCtx.freshHole(SortTerm.Type0, pos)._2;
    return new PiTerm(new Term.Param(new LocalVar(genName, pos), domain, explicit), codomain);
  }

  private @NotNull Result fail(@NotNull AyaDocile expr, @NotNull Problem prob) {
    return fail(expr, ErrorTerm.typeOf(expr), prob);
  }

  private @NotNull Result fail(@NotNull AyaDocile expr, @NotNull Term term, @NotNull Problem prob) {
    reporter.report(prob);
    return new TermResult(new ErrorTerm(expr), term);
  }

  @SuppressWarnings("unchecked") private @NotNull Result inferRef(@NotNull DefVar<?, ?> var) {
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
      var type = PiTerm.make(tele, Def.defResult(conVar));
      var telescopes = CtorDef.telescopes(conVar).rename();
      var body = telescopes.toConCall(conVar, 0);
      return new TermResult(LamTerm.make(telescopes.params(), body), type);
    } else if (var.core instanceof FieldDef || var.concrete instanceof TeleDecl.StructField) {
      // the code runs to here because we are tycking a StructField in a StructDecl
      // there should be two-stage check for this case:
      //  - check the definition's correctness: happens here
      //  - check the field value's correctness: happens in `visitNew` after the body was instantiated
      var field = (DefVar<FieldDef, TeleDecl.StructField>) var;
      return new TermResult(new RefTerm.Field(field), Def.defType(field));
    } else {
      final var msg = "Def var `" + var.name() + "` has core `" + var.core + "` which we don't know.";
      throw new InternalException(msg);
    }
  }

  private @NotNull <D extends Def, S extends Decl & Decl.Telescopic<?>> ExprTycker.Result defCall(DefVar<D, S> defVar, Callable.Factory<D, S> function) {
    var tele = Def.defTele(defVar);
    var teleRenamed = tele.map(Term.Param::rename);
    // unbound these abstracted variables
    Term body = function.make(defVar, 0, teleRenamed.map(Term.Param::toArg));
    var type = PiTerm.make(tele, Def.defResult(defVar));
    if ((defVar.core instanceof FnDef fn && fn.modifiers.contains(Modifier.Inline)) || defVar.core instanceof PrimDef) {
      body = whnf(body);
    }
    return new TermResult(LamTerm.make(teleRenamed, body), type);
  }

  /** @return null if unified successfully */
  private Unifier.FailureData unifyTy(@NotNull Term upper, @NotNull Term lower, @NotNull SourcePos pos) {
    tracing(builder -> builder.append(
      new Trace.UnifyT(lower.freezeHoles(state), upper.freezeHoles(state), pos)));
    var unifier = unifier(pos, Ordering.Lt);
    if (!unifier.compare(lower, upper, null)) return unifier.getFailure();
    else return null;
  }

  public @NotNull Unifier unifier(@NotNull SourcePos pos, @NotNull Ordering ord) {
    return unifier(pos, ord, localCtx);
  }

  /**
   * Check if <code>lower</code> is a subtype of <code>upper</code>,
   * and report a type error if it's not the case.
   *
   * @see ExprTycker#inheritFallbackUnify(Term, Result, Expr)
   */
  public void unifyTyReported(@NotNull Term upper, @NotNull Term lower, Expr loc) {
    var unification = unifyTy(upper, lower, loc.sourcePos());
    if (unification != null) reporter.report(new UnifyError.Type(loc, upper, lower, unification, state));
  }

  /**
   * Check if <code>lower</code> is a subtype of <code>upper</code>,
   * and try to insert implicit arguments to fulfill this goal (if possible).
   *
   * @return the term and type after insertion
   * @see ExprTycker#unifyTyReported(Term, Term, Expr)
   */
  private Result inheritFallbackUnify(@NotNull Term upper, @NotNull Result result, Expr loc) {
    var inst = instImplicits(result, loc.sourcePos());
    var term = inst.wellTyped();
    var lower = inst.type();
    var upperWHNF = whnf(upper);
    if (upperWHNF instanceof PathTerm path) {
      var res = tryEtaCompatiblePath(loc, term, lower, path);
      if (res != null) return res;
    } else if (whnf(lower) instanceof PathTerm cube && cube.params().sizeEquals(1)) {
      // TODO: also support n-ary path
      if (upperWHNF instanceof PiTerm pi && pi.param().explicit() && pi.param().type() == IntervalTerm.INSTANCE) {
        var lamBind = new RefTerm(new LocalVar(cube.params().first().name()));
        var body = new PAppTerm(term, cube, new Arg<>(lamBind, true));
        var inner = inheritFallbackUnify(pi.substBody(lamBind),
          new TermResult(body, cube.substType(SeqView.of(lamBind))), loc);
        var lamParam = new Term.Param(lamBind.var(), IntervalTerm.INSTANCE, true);
        return new TermResult(new LamTerm(lamParam, inner.wellTyped()), pi);
      }
    }
    var failureData = unifyTy(upper, lower, loc.sourcePos());
    if (failureData == null) return inst;
    return fail(term.freezeHoles(state), upper, new UnifyError.Type(loc, upper.freezeHoles(state), lower.freezeHoles(state), failureData, state));
  }

  private @Nullable TermResult tryEtaCompatiblePath(Expr loc, Term term, Term lower, PathTerm path) {
    int sizeLimit = path.params().size();
    var list = MutableArrayList.<LocalVar>create(sizeLimit);
    var innerMost = PiTerm.unpiOrPath(lower, term, this::whnf, list, sizeLimit);
    if (!list.sizeEquals(sizeLimit)) return null;
    unifyTyReported(path.computePi(), PiTerm.makeIntervals(list, innerMost.type), loc);
    var checked = checkBoundaries(loc, path, new Subst(), LamTerm.makeIntervals(list, innerMost.wellTyped));
    return lower instanceof PathTerm actualPath
      ? new TermResult(actualPath.eta(checked.wellTyped), actualPath)
      : new TermResult(path.eta(checked.wellTyped), checked.type);
  }

  private @NotNull Term mockTerm(Term.Param param, SourcePos pos) {
    // TODO: maybe we should create a concrete hole and check it against the type
    //  in case we can synthesize this term via its type only
    var genName = param.ref().name().concat(Constants.GENERATED_POSTFIX);
    return localCtx.freshHole(param.type(), genName, pos)._2;
  }

  private @NotNull Arg<Term> mockArg(Term.Param param, SourcePos pos) {
    return new Arg<>(mockTerm(param, pos), param.explicit());
  }

  public @NotNull Result check(@NotNull Expr expr, @NotNull Term type) {
    return withResult(type, () -> inherit(expr, type));
  }

  public boolean isPropType(@NotNull Term type) {
    var sort = type.computeType(state, localCtx).normalize(state, NormalizeMode.WHNF);
    if (sort instanceof MetaTerm meta) {
      var value = state.metas().getOption(meta.ref());
      if (value.isDefined()) return isPropType(value.get());
      state.metaNotProps().add(meta.ref()); // assert not Prop
      return false;
    }
    if (sort instanceof SortTerm s) return s.isProp();
    if (sort instanceof ErrorTerm) return false;
    throw new InternalException("Expected computeType() to produce a sort, got "
      + type.toDoc(AyaDistillerOptions.pretty())
      + " : " + sort.toDoc(AyaDistillerOptions.pretty()));
  }

  public interface Result {
    @NotNull Term wellTyped();
    @NotNull Term type();
    @NotNull Result freezeHoles(@NotNull TyckState state);
    default @NotNull Result normalize(@NotNull NormalizeMode mode, @NotNull TyckState state) {
      return new TermResult(wellTyped().normalize(state, mode), type().normalize(state, mode));
    }
  }

  /// region Helper

  public <R> R subscoped(@NotNull Supplier<R> action) {
    var parent = this.localCtx;
    this.localCtx = parent.deriveMap();
    var result = action.get();
    this.localCtx = parent;
    return result;
  }

  /// endregion

  /**
   * {@link TermResult#type} is the type of {@link TermResult#wellTyped}.
   *
   * @author ice1000
   */
  public record TermResult(@Override @NotNull Term wellTyped, @Override @NotNull Term type) implements Result {
    public static @NotNull TermResult error(@NotNull AyaDocile description) {
      return new TermResult(ErrorTerm.unexpected(description), ErrorTerm.typeOf(description));
    }

    @Override public @NotNull TermResult freezeHoles(@NotNull TyckState state) {
      return new TermResult(wellTyped.freezeHoles(state), type.freezeHoles(state));
    }
  }

  public record TyResult(@Override @NotNull Term wellTyped, @Override @NotNull SortTerm type) implements Result {
    @Override public @NotNull TyResult freezeHoles(@NotNull TyckState state) {
      return new TyResult(wellTyped.freezeHoles(state), type);
    }
  }

  public record SortResult(@Override @NotNull SortTerm wellTyped) implements Result {
    @Override public @NotNull SortTerm type() {
      return wellTyped.succ();
    }

    @Override public @NotNull ExprTycker.SortResult freezeHoles(@NotNull TyckState state) {
      return this;
    }
  }
}

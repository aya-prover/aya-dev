// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
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
import org.aya.core.visitor.DeltaExpander;
import org.aya.core.visitor.Subst;
import org.aya.generic.*;
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
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.Unifier;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * @apiNote make sure to instantiate this class once for each {@link Decl.TopLevel}.
 * Do <em>not</em> use multiple instances in the tycking of one {@link Decl.TopLevel}
 * and do <em>not</em> reuse instances of this class in the tycking of multiple {@link Decl.TopLevel}s.
 */
public final class ExprTycker extends Tycker {
  public @NotNull LocalCtx localCtx = new MapLocalCtx();
  public @NotNull AyaShape.Factory shapeFactory;

  private @NotNull Result doSynthesize(@NotNull Expr expr) {
    return switch (expr) {
      case Expr.LamExpr lam -> inherit(lam, generatePi(lam));
      case Expr.SortExpr sort -> sort(sort);
      case Expr.RefExpr ref -> switch (ref.resolvedVar()) {
        case LocalVar loc -> {
          var ty = localCtx.get(loc);
          yield new TermResult(new RefTerm(loc), ty);
        }
        case DefVar<?, ?> defVar -> inferRef(ref.sourcePos(), defVar);
        default -> throw new InternalException("Unknown var: " + ref.resolvedVar().getClass());
      };
      case Expr.PiExpr pi -> sort(pi);
      case Expr.SigmaExpr sigma -> sort(sigma);
      case Expr.LiftExpr lift -> {
        var result = synthesize(lift.expr());
        var levels = lift.lift();
        yield new TermResult(result.wellTyped().lift(levels), result.type().lift(levels));
      }
      case Expr.NewExpr newExpr -> {
        var structExpr = newExpr.struct();
        var struct = instImplicits(synthesize(structExpr).wellTyped(), structExpr.sourcePos());
        if (!(struct instanceof CallTerm.Struct structCall))
          yield fail(structExpr, struct, BadTypeError.structCon(state, newExpr, struct));
        var structRef = structCall.ref();
        var subst = new Subst(Def.defTele(structRef).map(Term.Param::ref), structCall.args().map(Arg::term));

        var fields = MutableList.<Tuple2<DefVar<FieldDef, TeleDecl.StructField>, Term>>create();
        var missing = MutableList.<AnyVar>create();
        var conFields = MutableMap.from(newExpr.fields().view().map(t -> Tuple.of(t.name().data(), t)));

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
            yield fail(newExpr, structCall, new FieldError.ArgMismatch(newExpr.sourcePos(), defField, bindings.size()));
          }
          var fieldExpr = bindings.zipView(telescope).foldRight(conField.body(), (pair, lamExpr) -> new Expr.LamExpr(conField.body().sourcePos(), new Expr.Param(pair._1.sourcePos(), pair._1.data(), pair._2.explicit()), lamExpr));
          var field = inherit(fieldExpr, type).wellTyped();
          fields.append(Tuple.of(fieldRef, field));
          subst.add(fieldRef, field);
        }

        if (missing.isNotEmpty())
          yield fail(newExpr, structCall, new FieldError.MissingField(newExpr.sourcePos(), missing.toImmutableSeq()));
        if (conFields.isNotEmpty())
          yield fail(newExpr, structCall, new FieldError.NoSuchField(newExpr.sourcePos(), conFields.keysView().toImmutableSeq()));
        yield new TermResult(new IntroTerm.New(structCall, ImmutableMap.from(fields)), structCall);
      }
      case Expr.ProjExpr proj -> {
        var struct = proj.tup();
        var projectee = instImplicits(synthesize(struct), struct.sourcePos());
        yield proj.ix().fold(ix -> {
          if (!(projectee.type() instanceof FormTerm.Sigma(var telescope)))
            return fail(struct, projectee.type(), BadTypeError.sigmaAcc(state, struct, ix, projectee.type()));
          var index = ix - 1;
          if (index < 0 || index >= telescope.size())
            return fail(proj, new TupleError.ProjIxError(proj, ix, telescope.size()));
          var type = telescope.get(index).type();
          var subst = ElimTerm.Proj.projSubst(projectee.wellTyped(), index, telescope);
          return new TermResult(ElimTerm.proj(projectee.wellTyped(), ix), type.subst(subst));
        }, sp -> {
          var fieldName = sp.justName();
          if (!(projectee.type() instanceof CallTerm.Struct structCall))
            return fail(struct, ErrorTerm.unexpected(projectee.type()), BadTypeError.structAcc(state, struct, fieldName, projectee.type()));
          var structCore = structCall.ref().core;
          if (structCore == null) throw new UnsupportedOperationException("TODO");
          // TODO[ice]: instantiate the type
          if (!(proj.resolvedVar() instanceof DefVar<?, ?> defVar && defVar.core instanceof FieldDef field))
            return fail(proj, new FieldError.UnknownField(sp.sourcePos(), fieldName));
          var fieldRef = field.ref();

          var structSubst = DeltaExpander.buildSubst(structCore.telescope(), structCall.args());
          var tele = Term.Param.subst(fieldRef.core.selfTele, structSubst, 0);
          var teleRenamed = tele.map(Term.Param::rename);
          var access = new CallTerm.Access(projectee.wellTyped(), fieldRef, structCall.args(), teleRenamed.map(Term.Param::toArg));
          return new TermResult(IntroTerm.Lambda.make(teleRenamed, access), FormTerm.Pi.make(tele, field.result().subst(structSubst)));
        });
      }
      case Expr.TupExpr tuple -> {
        var items = tuple.items().map(this::synthesize);
        yield new TermResult(new IntroTerm.Tuple(items.map(Result::wellTyped)), new FormTerm.Sigma(items.map(item -> new Term.Param(Constants.anonymous(), item.type(), true))));
      }
      case Expr.CoeExpr coe -> {
        assert coe.resolvedVar() instanceof DefVar<?, ?> defVar
          && defVar.core instanceof PrimDef def && def.id == PrimDef.ID.COE : "desugar bug";
        var defVar = coe.resolvedVar();
        var mockApp = new Expr.AppExpr(coe.sourcePos(), new Expr.AppExpr(coe.sourcePos(),
          new Expr.RefExpr(coe.id().sourcePos(), defVar),
          new Expr.NamedArg(true, coe.type())),
          new Expr.NamedArg(true, coe.restr()));
        var res = synthesize(mockApp);
        if (whnf(res.wellTyped()) instanceof PrimTerm.Coe(var type, var restr) && !(type instanceof ErrorTerm)) {
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
            Function<Integer, Boolean> post = usages -> {
              if (usages != 0) bad.typeSubst = typeSubst;
              return usages == 0;
            };
            return switch (typeSubst) {
              case IntroTerm.Lambda(var param, var body) -> post.apply(body.findUsages(param.ref()));
              case IntroTerm.PathLam(var params, var body) ->
                post.apply(params.view().map(body::findUsages).foldLeft(0, Integer::sum));
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
      case Expr.AppExpr appE -> {
        var f = synthesize(appE.function());
        if (f.wellTyped() instanceof ErrorTerm || f.type() instanceof ErrorTerm) yield f;
        var app = f.wellTyped();
        var argument = appE.argument();
        var fTy = whnf(f.type());
        var argLicit = argument.explicit();
        if (fTy instanceof CallTerm.Hole fTyHole) {
          // [ice] Cannot 'generatePi' because 'generatePi' takes the current contextTele,
          // but it may contain variables absent from the 'contextTele' of 'fTyHole.ref.core'
          var pi = fTyHole.asPi(argLicit);
          unifier(appE.sourcePos(), Ordering.Eq).compare(fTy, pi, null);
          fTy = whnf(fTy);
        }
        FormTerm.Cube cube;
        FormTerm.Pi pi;
        var subst = new Subst(MutableMap.create());
        try {
          var tup = ensurePiOrPath(fTy);
          pi = tup._1;
          cube = tup._2;
          while (pi.param().explicit() != argLicit || argument.name() != null && !Objects.equals(pi.param().ref().name(), argument.name())) {
            if (argLicit || argument.name() != null) {
              // that implies paramLicit == false
              var holeApp = mockArg(pi.param().subst(subst), argument.expr().sourcePos());
              // path types are always explicit
              app = ElimTerm.make(app, holeApp);
              subst.addDirectly(pi.param().ref(), holeApp.term());
              tup = ensurePiOrPath(pi.body());
              pi = tup._1;
              if (tup._2 != null) cube = tup._2;
            } else yield fail(appE, new ErrorTerm(pi.body()), new LicitError.UnexpectedImplicitArg(argument));
          }
          tup = ensurePiOrPath(pi.subst(subst));
          pi = tup._1;
          if (tup._2 != null) cube = tup._2;
        } catch (NotPi notPi) {
          yield fail(expr, ErrorTerm.unexpected(notPi.what), BadTypeError.pi(state, expr, notPi.what));
        }
        var elabArg = inherit(argument.expr(), pi.param().type()).wellTyped();
        subst.addDirectly(pi.param().ref(), elabArg);
        var arg = new Arg<>(elabArg, argLicit);
        var newApp = cube == null
          ? ElimTerm.make(app, arg)
          : cube.makeApp(app, arg).subst(subst);
        // ^ instantiate inserted implicits to the partial element in `Cube`.
        // It is better to `cube.subst().makeApp()`, but we don't have a `subst` method for `Cube`.
        // Anyway, the `Term.descent` will recurse into the `Cube` for `PathApp` and substitute the partial element.
        yield new TermResult(newApp, pi.body().subst(subst));
      }
      case Expr.HoleExpr hole ->
        inherit(hole, localCtx.freshHole(null, Constants.randomName(hole), hole.sourcePos())._2);
      case Expr.ErrorExpr err -> TermResult.error(err.description());
      case Expr.LitIntExpr lit -> {
        int integer = lit.integer();
        // TODO[literal]: int literals. Currently the parser does not allow negative literals.
        var defs = shapeFactory.findImpl(AyaShape.NAT_SHAPE);
        if (defs.isEmpty()) yield fail(expr, new NoRuleError(expr, null));
        if (defs.sizeGreaterThan(1)) yield fail(expr, new LiteralError.AmbiguousLit(expr, defs));
        var type = new CallTerm.Data(((DataDef) defs.first()).ref, 0, ImmutableSeq.empty());
        yield new TermResult(new LitTerm.ShapedInt(integer, AyaShape.NAT_SHAPE, type), type);
      }
      case Expr.LitStringExpr litStr -> {
        if (!state.primFactory().have(PrimDef.ID.STRING))
          yield fail(expr, new NoRuleError(expr, null));

        yield new TermResult(new PrimTerm.Str(litStr.string()), state.primFactory().getCall(PrimDef.ID.STRING));
      }
      case Expr.Path path -> localCtx.withIntervals(path.params().view(), () -> {
        var type = synthesize(path.type());
        var partial = elaboratePartial(path.partial(), type.wellTyped());
        var cube = new FormTerm.Cube(path.params(), type.wellTyped(), partial);
        return new TermResult(new FormTerm.Path(cube), type.type());
      });
      case Expr.Array arr when arr.arrayBlock().isRight() -> {
        var arrayBlock = arr.arrayBlock().getRightValue();
        var elements = arrayBlock.exprList();

        // find def
        var defs = shapeFactory.findImpl(AyaShape.LIST_SHAPE);
        if (defs.isEmpty()) yield fail(expr, new NoRuleError(expr, null));
        if (defs.sizeGreaterThan(1)) yield fail(expr, new LiteralError.AmbiguousLit(expr, defs));
        var def = (DataDef) defs.first();

        // preparing
        var dataParam = def.telescope().first();
        var sort = dataParam.type();    // the sort of type below.
        var hole = localCtx.freshHole(sort, arr.sourcePos());
        var type = new CallTerm.Data(def.ref(), 0, ImmutableSeq.of(
          new Arg<>(hole._1, dataParam.explicit())));

        // do type check
        var results = elements.map(element -> inherit(element, hole._1).wellTyped());
        yield new TermResult(new LitTerm.ShapedList(results, AyaShape.LIST_SHAPE, type), type);
      }
      default -> fail(expr, new NoRuleError(expr, null));
    };
  }

  public @NotNull Restr<Term> restr(@NotNull Restr<Expr> restr) {
    return restr.mapCond(this::condition);
  }

  private @NotNull Restr.Cond<Term> condition(@NotNull Restr.Cond<Expr> c) {
    // forall i. (c_i is valid)
    return new Restr.Cond<>(inherit(c.inst(), PrimTerm.Interval.INSTANCE).wellTyped(), c.isOne());
    // ^ note: `inst` may be ErrorTerm!
  }

  private @NotNull Partial<Term> elaboratePartial(@NotNull Expr.PartEl partial, @NotNull Term type) {
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
    return switch (CofThy.isOne(whnf(inherit(lhs, PrimTerm.Interval.INSTANCE).wellTyped()))) {
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
    while (term instanceof IntroTerm.Lambda intro && !intro.param().explicit()) {
      term = whnf(ElimTerm.make(intro, mockArg(intro.param(), pos)));
    }
    return term;
  }

  private Result instImplicits(@NotNull Result result, @NotNull SourcePos pos) {
    var type = whnf(result.type());
    var term = result.wellTyped();
    while (type instanceof FormTerm.Pi pi && !pi.param().explicit()) {
      var holeApp = mockArg(pi.param(), pos);
      term = ElimTerm.make(term, holeApp);
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

  private Tuple2<FormTerm.Pi, FormTerm.@Nullable Cube>
  ensurePiOrPath(@NotNull Term term) throws NotPi {
    term = whnf(term);
    if (term instanceof FormTerm.Pi pi) return Tuple.of(pi, null);
    if (term instanceof FormTerm.Path(var cube))
      return Tuple.of(cube.computePi(), cube);
    else throw new NotPi(term);
  }

  private @NotNull Result doInherit(@NotNull Expr expr, @NotNull Term term) {
    return switch (expr) {
      case Expr.TupExpr(var pos, var it) -> {
        var items = MutableList.<Term>create();
        var resultTele = MutableList.<Term.@NotNull Param>create();
        var typeWHNF = whnf(term);
        if (typeWHNF instanceof CallTerm.Hole hole) yield unifyTyMaybeInsert(hole, synthesize(expr), expr);
        if (!(typeWHNF instanceof FormTerm.Sigma(var params)))
          yield fail(expr, term, BadTypeError.sigmaCon(state, expr, typeWHNF));
        var againstTele = params.view();
        var last = params.last().type();
        var subst = new Subst(MutableMap.create());
        for (var iter = it.iterator(); iter.hasNext(); ) {
          var item = iter.next();
          var first = againstTele.first().subst(subst);
          var result = inherit(item, first.type());
          items.append(result.wellTyped());
          var ref = first.ref();
          resultTele.append(new Term.Param(ref, result.type(), first.explicit()));
          againstTele = againstTele.drop(1);
          if (againstTele.isNotEmpty()) subst.add(ref, result.wellTyped());
          else if (iter.hasNext()) {
            yield fail(expr, term, new TupleError.ElemMismatchError(pos, params.size(), it.size()));
          } else items.append(inherit(item, last.subst(subst)).wellTyped());
        }
        var resTy = new FormTerm.Sigma(resultTele.toImmutableSeq());
        yield new TermResult(new IntroTerm.Tuple(items.toImmutableSeq()), resTy);
      }
      case Expr.HoleExpr hole -> {
        // TODO[ice]: deal with unit type
        var freshHole = localCtx.freshHole(term, Constants.randomName(hole), hole.sourcePos());
        if (hole.explicit()) reporter.report(new Goal(state, freshHole._1, hole.accessibleLocal().get()));
        yield new TermResult(freshHole._2, term);
      }
      case Expr.SortExpr sortExpr -> {
        var result = sort(sortExpr);
        var normTerm = whnf(term);
        if (normTerm instanceof FormTerm.Sort sort) {
          var unifier = unifier(sortExpr.sourcePos(), Ordering.Lt);
          unifier.compareSort(result.type(), sort);
        } else {
          unifyTyReported(result.type(), normTerm, sortExpr);
        }
        yield new TermResult(result.wellTyped(), normTerm);
      }
      case Expr.LamExpr lam -> {
        if (term instanceof CallTerm.Hole) unifyTy(term, generatePi(lam), lam.sourcePos());
        yield switch (whnf(term)) {
          case FormTerm.Pi dt -> {
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
            var resultParam = new Term.Param(var, type, param.explicit());
            var body = dt.substBody(resultParam.toTerm());
            yield localCtx.with(resultParam, () -> {
              var rec = check(lam.body(), body);
              return new TermResult(new IntroTerm.Lambda(resultParam, rec.wellTyped()), dt);
            });
          }
          // Path lambda!
          case FormTerm.Path path -> checkBoundaries(expr, path, new Subst(),
            inherit(expr, path.cube().computePi()).wellTyped());
          default -> fail(lam, term, BadTypeError.pi(state, lam, term));
        };
      }
      case Expr.LitIntExpr(var pos, var end) -> {
        var ty = whnf(term);
        if (ty instanceof PrimTerm.Interval) {
          if (end == 0 || end == 1) yield new TermResult(end == 0 ? PrimTerm.Mula.LEFT : PrimTerm.Mula.RIGHT, ty);
          else yield fail(expr, new PrimError.BadInterval(pos, end));
        }
        if (ty instanceof CallTerm.Data dataCall) {
          var data = dataCall.ref().core;
          var shape = shapeFactory.find(data);
          if (shape.isDefined())
            yield new TermResult(new LitTerm.ShapedInt(end, shape.get(), dataCall), term);
        }
        if (ty instanceof CallTerm.Hole hole) {
          var nat = shapeFactory.findImpl(AyaShape.NAT_SHAPE);
          // When there's more than one Nat, delay the unification for cases like
          // def foo : Option Nat1 => some 0
          // def bar : Option Nat2 => some 1
          if (nat.sizeGreaterThan(1))
            yield new TermResult(new LitTerm.ShapedInt(end, AyaShape.NAT_SHAPE, hole), term);
          // fallthrough: When there's only one Nat, solve the hole now.
          // Note: if no Nat was found, errors will be reported in `synthesize(expr)`
        }
        yield unifyTyMaybeInsert(term, synthesize(expr), expr);
      }
      case Expr.PartEl el -> {
        if (!(whnf(term) instanceof FormTerm.PartTy ty)) yield fail(el, term, BadTypeError.partTy(state, el, term));
        var cofTy = ty.restr();
        var rhsType = ty.type();
        var partial = elaboratePartial(el, rhsType);
        var face = partial.restr();
        if (!CofThy.conv(cofTy, new Subst(), subst -> CofThy.satisfied(subst.restr(state, face))))
          yield fail(el, new CubicalError.FaceMismatch(el, face, cofTy));
        yield new TermResult(new IntroTerm.PartEl(partial, rhsType), ty);
      }
      default -> unifyTyMaybeInsert(term, synthesize(expr), expr);
    };
  }

  private TermResult checkBoundaries(Expr expr, FormTerm.Path path, Subst subst, Term lambda) {
    var cube = path.cube();
    var applied = cube.applyDimsTo(lambda);
    return localCtx.withIntervals(cube.params().view(), () -> {
      var happy = switch (cube.partial()) {
        case Partial.Const<Term> sad -> boundary(expr, applied, sad.u(), cube.type(), subst);
        case Partial.Split<Term> hap -> hap.clauses().allMatch(c ->
          CofThy.conv(c.cof(), subst, s -> boundary(expr, applied, c.u(), cube.type(), s)));
      };
      return happy ? new TermResult(new IntroTerm.PathLam(cube.params(), applied), path)
        : new TermResult(ErrorTerm.unexpected(expr), path);
    });
  }

  public static @NotNull FormTerm.Sort calculateSigma(@NotNull FormTerm.Sort x, @NotNull FormTerm.Sort y) throws IllegalArgumentException {
    int lift = Math.max(x.lift(), y.lift());
    if(x.kind() == SortKind.Prop || y.kind() == SortKind.Prop) {
      return FormTerm.Prop.INSTANCE;
    } else if (x.kind() == SortKind.Set || y.kind() == SortKind.Set) {
      return new FormTerm.Set(lift);
    } else if (x.kind() == SortKind.Type || y.kind() == SortKind.Type) {
      return new FormTerm.Type(lift);
    } else if (x instanceof FormTerm.ISet && y instanceof FormTerm.ISet) {
      return FormTerm.ISet.INSTANCE; // TODO: should we allow this?
    }
    throw new IllegalArgumentException(); // TODO: better error reporting
  }

  private @NotNull SortResult doSort(@NotNull Expr expr) {
    var univ = FormTerm.Type.ZERO;
    return switch (expr) {
      case Expr.TupExpr tuple -> failSort(tuple, BadTypeError.sigmaCon(state, tuple, univ));
      case Expr.HoleExpr hole -> {
        var freshHole = localCtx.freshHole(univ, Constants.randomName(hole), hole.sourcePos());
        if (hole.explicit()) reporter.report(new Goal(state, freshHole._1, hole.accessibleLocal().get()));
        yield new SortResult(freshHole._2, univ);
      }
      case Expr.SortExpr sortExpr -> {
        var self = switch (sortExpr) {
          case Expr.TypeExpr ty -> new FormTerm.Type(ty.lift());
          case Expr.SetExpr set -> new FormTerm.Set(set.lift());
          case Expr.PropExpr prop -> FormTerm.Prop.INSTANCE;
          case Expr.ISetExpr iset -> FormTerm.ISet.INSTANCE;
        };
        yield new SortResult(self, self.succ());
      }
      case Expr.LamExpr lam -> failSort(lam, BadTypeError.pi(state, lam, univ));
      case Expr.PartEl el -> failSort(el, BadTypeError.partTy(state, el, univ));
      case Expr.PiExpr pi -> {
        var param = pi.param();
        final var var = param.ref();
        var domTy = param.type();
        var domRes = sort(domTy);
        var resultParam = new Term.Param(var, domRes.wellTyped(), param.explicit());
        yield localCtx.with(resultParam, () -> {
          var cod = sort(pi.last());
          return new SortResult(new FormTerm.Pi(resultParam, cod.wellTyped()), sortPi(pi, domRes.type(), cod.type()));
        });
      }
      case Expr.SigmaExpr sigma -> {
        var resultTele = MutableList.<Tuple3<LocalVar, Boolean, Term>>create();
        var resultTypes = MutableList.<FormTerm.Sort>create();
        for (var tuple : sigma.params()) {
          var result = sort(tuple.type());
          resultTypes.append(result.type());
          var ref = tuple.ref();
          localCtx.put(ref, result.wellTyped());
          resultTele.append(Tuple.of(ref, tuple.explicit(), result.wellTyped()));
        }
        var unifier = unifier(sigma.sourcePos(), Ordering.Lt);
        var maxSort = resultTypes.reduce(ExprTycker::calculateSigma);
        if (!(maxSort instanceof FormTerm.Prop)) resultTypes.forEach(t -> unifier.compareSort(t, maxSort));
        localCtx.remove(sigma.params().view().map(Expr.Param::ref));
        yield new SortResult(new FormTerm.Sigma(Term.Param.fromBuffer(resultTele)), maxSort);
      }
      default -> {
        var result = synthesize(expr);
        yield new SortResult(result.wellTyped(), ty(expr, result.type()));
      }
    };
  }

  public @NotNull TyResult ty(@NotNull Expr expr) {
    return new TyResult(ty(expr, sort(expr).wellTyped()));
  }

  private @NotNull FormTerm.Sort ty(@NotNull Expr errorMsg, @NotNull Term term) {
    return switch (whnf(term)) {
      case FormTerm.Sort u -> u;
      case CallTerm.Hole hole -> {
        unifyTyReported(hole, FormTerm.Type.ZERO, errorMsg);
        yield FormTerm.Type.ZERO;
      }
      default -> {
        reporter.report(BadTypeError.univ(state, errorMsg, term));
        yield FormTerm.Type.ZERO;
      }
    };
  }

  private void traceExit(Result result, @NotNull Expr expr) {
    var frozen = LazyValue.of(() -> result.freezeHoles(state));
    tracing(builder -> {
      builder.append(new Trace.TyckT(frozen.get(), expr.sourcePos()));
      builder.reduce();
    });
    if (expr instanceof Expr.WithTerm withTerm) withTerm.theCore().set(frozen.get());
  }

  public ExprTycker(@NotNull PrimDef.Factory primFactory, @NotNull AyaShape.Factory shapeFactory, @NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder) {
    super(reporter, new TyckState(primFactory), traceBuilder);
    this.shapeFactory = shapeFactory;
  }

  public @NotNull Result inherit(@NotNull Expr expr, @NotNull Term type) {
    tracing(builder -> builder.shift(new Trace.ExprT(expr, type.freezeHoles(state))));
    Result result;
    if (type instanceof FormTerm.Pi pi && !pi.param().explicit() && needImplicitParamIns(expr)) {
      var implicitParam = new Term.Param(new LocalVar(Constants.ANONYMOUS_PREFIX), pi.param().type(), false);
      var body = localCtx.with(implicitParam, () -> inherit(expr, pi.substBody(implicitParam.toTerm()))).wellTyped();
      result = new TermResult(new IntroTerm.Lambda(implicitParam, body), pi);
    } else result = doInherit(expr, type).checkErased(expr, state, localCtx);
    traceExit(result, expr);
    return result;
  }

  public @NotNull Result synthesize(@NotNull Expr expr) {
    tracing(builder -> builder.shift(new Trace.ExprT(expr, null)));
    var res = doSynthesize(expr).checkErased(expr, state, localCtx);
    traceExit(res, expr);
    return res;
  }

  public @NotNull SortResult sort(@NotNull Expr expr) {
    return sort(expr, -1);
  }

  public @NotNull SortResult sort(@NotNull Expr expr, int upperBound) {
    tracing(builder -> builder.shift(new Trace.ExprT(expr, null)));
    var result = doSort(expr);
    if (upperBound != -1 && upperBound < result.type().lift())
      reporter.report(new LevelError(expr.sourcePos(), new FormTerm.Type(upperBound), result.type(), true));
    traceExit(result, expr);
    return result;
  }

  public @NotNull FormTerm.Sort sortPi(@Nullable Expr expr, @NotNull FormTerm.Sort domain, @NotNull FormTerm.Sort codomain) {
    return sortPi(this.reporter, expr, domain, codomain);
  }

  public static @NotNull FormTerm.Sort sortPi(@NotNull FormTerm.Sort domain, @NotNull FormTerm.Sort codomain) throws IllegalArgumentException {
    return sortPi(null, null, domain, codomain);
  }

  public static @NotNull FormTerm.Sort sortPi(@Nullable Reporter reporter, @Nullable Expr expr, @NotNull FormTerm.Sort domain, @NotNull FormTerm.Sort codomain) throws IllegalArgumentException {
    var result = switch (domain) {
      case FormTerm.Type a -> switch (codomain) {
        case FormTerm.Type b -> new FormTerm.Type(Math.max(a.lift(), b.lift()));
        case FormTerm.Set b -> new FormTerm.Type(Math.max(a.lift(), b.lift()));
        case FormTerm.ISet b -> new FormTerm.Set(a.lift());
        case FormTerm.Prop prop -> FormTerm.Prop.INSTANCE;
      };
      case FormTerm.ISet a -> switch (codomain) {
        case FormTerm.ISet b -> FormTerm.Set.ZERO;
        case FormTerm.Set b -> b;
        case FormTerm.Type b -> b;
        default -> null;
      };
      case FormTerm.Set a -> switch (codomain) {
        case FormTerm.Set b -> new FormTerm.Set(Math.max(a.lift(), b.lift()));
        case FormTerm.Type b -> new FormTerm.Set(Math.max(a.lift(), b.lift()));
        case FormTerm.ISet b -> new FormTerm.Set(a.lift());
        default -> null;
      };
      case FormTerm.Prop a -> switch (codomain) {
        case FormTerm.Prop b -> FormTerm.Prop.INSTANCE;
        case FormTerm.Type b -> b;
        default -> null;
      };
    };
    if (result == null) {
      if (reporter == null) {
        throw new IllegalArgumentException("Cannot construct a pi type from " + domain + " -> " + codomain);
      } else {
        reporter.report(new SortPiError(expr.sourcePos(), domain, codomain));
      }
      return FormTerm.Type.ZERO;
    } else {
      return result;
    }
  }

  private static boolean needImplicitParamIns(@NotNull Expr expr) {
    return expr instanceof Expr.LamExpr ex && ex.param().explicit() || !(expr instanceof Expr.LamExpr);
  }

  public @NotNull Result zonk(@NotNull Result result) {
    solveMetas();
    return new TermResult(zonk(result.wellTyped()), zonk(result.type()));
  }

  private @NotNull Term generatePi(Expr.@NotNull LamExpr expr) {
    var param = expr.param();
    return generatePi(expr.sourcePos(), param.ref().name(), param.explicit());
  }

  private @NotNull Term generatePi(@NotNull SourcePos pos, @NotNull String name, boolean explicit) {
    var genName = name + Constants.GENERATED_POSTFIX;
    // [ice]: unsure if ZERO is good enough
    var domain = localCtx.freshHole(FormTerm.Type.ZERO, genName + "ty", pos)._2;
    var codomain = localCtx.freshHole(FormTerm.Type.ZERO, pos)._2;
    return new FormTerm.Pi(new Term.Param(new LocalVar(genName, pos), domain, explicit), codomain);
  }

  private @NotNull Result fail(@NotNull AyaDocile expr, @NotNull Problem prob) {
    return fail(expr, ErrorTerm.typeOf(expr), prob);
  }

  private @NotNull Result fail(@NotNull AyaDocile expr, @NotNull Term term, @NotNull Problem prob) {
    reporter.report(prob);
    return new TermResult(new ErrorTerm(expr), term);
  }

  private @NotNull SortResult failSort(@NotNull AyaDocile expr, @NotNull Problem prob) {
    reporter.report(prob);
    return new SortResult(new ErrorTerm(expr), FormTerm.Type.ZERO);
  }

  @SuppressWarnings("unchecked") private @NotNull Result inferRef(@NotNull SourcePos pos, @NotNull DefVar<?, ?> var) {
    if (var.core instanceof FnDef || var.concrete instanceof TeleDecl.FnDecl) {
      return defCall(pos, (DefVar<FnDef, TeleDecl.FnDecl>) var, CallTerm.Fn::new);
    } else if (var.core instanceof PrimDef) {
      return defCall(pos, (DefVar<PrimDef, TeleDecl.PrimDecl>) var, CallTerm.Prim::new);
    } else if (var.core instanceof DataDef || var.concrete instanceof TeleDecl.DataDecl) {
      return defCall(pos, (DefVar<DataDef, TeleDecl.DataDecl>) var, CallTerm.Data::new);
    } else if (var.core instanceof StructDef || var.concrete instanceof TeleDecl.StructDecl) {
      return defCall(pos, (DefVar<StructDef, TeleDecl.StructDecl>) var, CallTerm.Struct::new);
    } else if (var.core instanceof CtorDef || var.concrete instanceof TeleDecl.DataDecl.DataCtor) {
      var conVar = (DefVar<CtorDef, TeleDecl.DataDecl.DataCtor>) var;
      var tele = Def.defTele(conVar);
      var type = FormTerm.Pi.make(tele, Def.defResult(conVar));
      var telescopes = CtorDef.telescopes(conVar).rename();
      var body = telescopes.toConCall(conVar);
      return new TermResult(IntroTerm.Lambda.make(telescopes.params(), body), type);
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

  private @NotNull <D extends Def, S extends Decl & Decl.Telescopic> ExprTycker.Result defCall(@NotNull SourcePos pos, DefVar<D, S> defVar, CallTerm.Factory<D, S> function) {
    var tele = Def.defTele(defVar);
    var teleRenamed = tele.map(Term.Param::rename);
    // unbound these abstracted variables
    Term body = function.make(defVar, 0, teleRenamed.map(Term.Param::toArg));
    var type = FormTerm.Pi.make(tele, Def.defResult(defVar));
    if ((defVar.core instanceof FnDef fn && fn.modifiers.contains(Modifier.Inline)) || defVar.core instanceof PrimDef) {
      body = whnf(body);
    }
    return new TermResult(IntroTerm.Lambda.make(teleRenamed, body), type);
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
   * @see ExprTycker#unifyTyMaybeInsert(Term, Result, Expr)
   */
  void unifyTyReported(@NotNull Term upper, @NotNull Term lower, Expr loc) {
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
  private Result unifyTyMaybeInsert(@NotNull Term upper, @NotNull Result result, Expr loc) {
    var inst = instImplicits(result, loc.sourcePos());
    var term = inst.wellTyped();
    var lower = inst.type();
    if (upper instanceof FormTerm.Path path) {
      var checked = checkBoundaries(loc, path, new Subst(), term);
      return lower instanceof FormTerm.Path actualPath
        ? new TermResult(actualPath.cube().eta(checked.wellTyped()), actualPath)
        : new TermResult(path.cube().eta(checked.wellTyped()), checked.type);
    }
    var failureData = unifyTy(upper, lower, loc.sourcePos());
    if (failureData == null) return inst;
    return fail(term.freezeHoles(state), upper, new UnifyError.Type(loc, upper.freezeHoles(state), lower.freezeHoles(state), failureData, state));
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
    return checkIllegalErasure(expr.sourcePos(), inherit(expr, type));
  }

  public @NotNull Result checkIllegalErasure(@NotNull SourcePos sourcePos, @NotNull Result result) {
    return new TermResult(checkIllegalErasure(sourcePos, result.wellTyped(), result.type()), result.type());
  }

  public @NotNull Term checkIllegalErasure(@NotNull SourcePos sourcePos, @NotNull Term wellTyped, @NotNull Term type) {
    if (wellTyped instanceof FormTerm.Sort) return wellTyped;
    var sort = type.computeType(state, localCtx);
    if (sort instanceof FormTerm.Prop) return wellTyped;
    return checkIllegalErasure(sourcePos, wellTyped);
  }

  public @NotNull Term checkIllegalErasure(@NotNull SourcePos sourcePos, @NotNull Term term) {
    var checker = new Function<Term, Term>() {
      private @NotNull Term post(@NotNull Term term) {
        if (term instanceof FormTerm.Sort) return term;
        var erased = ElimTerm.underlyingIllegalErasure(term);
        if (erased != null) {
          reporter.report(new ErasedError(sourcePos, erased, null));
          return new ErrorTerm(term);
        }
        return term;
      }
      @Override
      public @NotNull Term apply(@NotNull Term term) {
        if (term instanceof IntroTerm.Lambda) return term;
        if (term instanceof ErasedTerm) return post(term);
        return post(term.descent(this));
      }
    };
    return checker.apply(term);
  }

  public interface Result {
    @NotNull Term wellTyped();
    @NotNull Term type();
    @NotNull Result freezeHoles(@NotNull TyckState state);

    default Result checkErased(@NotNull Expr expr, @NotNull TyckState state, @NotNull LocalCtx ctx) {
      if (wellTyped() instanceof FormTerm.Sort) return this;
      var type = type();
      var sort = type.computeType(state, ctx);
      if (sort instanceof FormTerm.Prop || ElimTerm.isErased(wellTyped()))
        return new TermResult(new ErasedTerm(type, sort instanceof FormTerm.Prop, expr.sourcePos()), type);
      return this;
    }
  }


  /**
   * {@link TermResult#type} is the type of {@link TermResult#wellTyped}.
   *
   * @author ice1000
   */
  public record TermResult(@Override @NotNull Term wellTyped, @Override @NotNull Term type) implements Result {
    @Contract(value = " -> new", pure = true) public @NotNull Tuple2<Term, Term> toTuple() {
      return Tuple.of(type, wellTyped);
    }

    public static @NotNull TermResult error(@NotNull AyaDocile description) {
      return new TermResult(ErrorTerm.unexpected(description), ErrorTerm.typeOf(description));
    }

    @Override public @NotNull TermResult freezeHoles(@NotNull TyckState state) {
      return new TermResult(wellTyped.freezeHoles(state), type.freezeHoles(state));
    }
  }

  public record SortResult(@Override @NotNull Term wellTyped, @Override @NotNull FormTerm.Sort type) implements Result {
    @Override public @NotNull SortResult freezeHoles(@NotNull TyckState state) {
      return new SortResult(wellTyped.freezeHoles(state), type);
    }
  }

  public record TyResult(@Override @NotNull FormTerm.Sort wellTyped) implements Result {
    @Override public @NotNull FormTerm.Sort type() {
      return wellTyped.succ();
    }

    @Override public @NotNull TyResult freezeHoles(@NotNull TyckState state) {
      return this;
    }
  }
}

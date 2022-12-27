// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Tuple3;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.FieldDef;
import org.aya.core.def.PrimDef;
import org.aya.core.repr.AyaShape;
import org.aya.core.term.*;
import org.aya.core.visitor.AyaRestrSimplifier;
import org.aya.core.visitor.DeltaExpander;
import org.aya.core.visitor.Subst;
import org.aya.core.visitor.Zonker;
import org.aya.generic.Constants;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.CofThy;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.error.*;
import org.aya.tyck.pat.PatTycker;
import org.aya.tyck.pat.TypedSubst;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.tycker.PropTycker;
import org.aya.tyck.tycker.TyckState;
import org.aya.util.Arg;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * @apiNote make sure to instantiate this class once for each {@link Decl.TopLevel}.
 * Do <em>not</em> use multiple instances in the tycking of one {@link Decl.TopLevel}
 * and do <em>not</em> reuse instances of this class in the tycking of multiple {@link Decl.TopLevel}s.
 */
public final class ExprTycker extends PropTycker {
  /**
   * a `let` sequence, consider we are tycking in
   * {@code let ... in HERE}
   */
  public @NotNull TypedSubst lets = new TypedSubst();
  public final @NotNull AyaShape.Factory shapeFactory;

  public <T> T withSubSubst(Supplier<T> supplier) {
    var oldLets = lets;
    lets = oldLets.derive();
    var result = supplier.get();
    lets = oldLets;
    return result;
  }

  private @NotNull Result doSynthesize(@NotNull Expr expr) {
    return switch (expr) {
      case Expr.Lambda lam when lam.param().type() instanceof Expr.Hole -> inherit(lam, generatePi(lam));
      case Expr.Lambda lam -> {
        var paramTy = ty(lam.param().type());
        yield localCtx.with(lam.param().ref(), paramTy, () -> {
          var body = synthesize(lam.body());
          var param = new Term.Param(lam.param(), paramTy);
          var pi = new PiTerm(param, body.type()).freezeHoles(state).rename();
          return new Result.Default(new LamTerm(param, body.wellTyped()), pi);
        });
      }
      case Expr.Sort sort -> {
        var ty = ty(sort);
        yield new Result.Default(ty, ty.lift(1));
      }
      case Expr.Ref ref -> switch (ref.resolvedVar()) {
        case LocalVar loc -> lets.getOption(loc).getOrElse(() -> {
          // not defined in lets, search localCtx
          var ty = localCtx.get(loc);
          return new Result.Default(new RefTerm(loc), ty);
        });
        case DefVar<?, ?> defVar -> inferRef(defVar);
        default -> throw new InternalException("Unknown var: " + ref.resolvedVar().getClass());
      };
      case Expr.Pi pi -> {
        var corePi = ty(pi);
        yield new Result.Default(corePi, corePi.computeType(state, localCtx));
      }
      case Expr.Sigma sigma -> {
        var coreSigma = ty(sigma);
        yield new Result.Default(coreSigma, coreSigma.computeType(state, localCtx));
      }
      case Expr.Lift lift -> {
        var result = synthesize(lift.expr());
        var levels = lift.lift();
        yield new Result.Default(result.wellTyped().lift(levels), result.type().lift(levels));
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
        yield new Result.Default(new NewTerm(structCall, ImmutableMap.from(fields)), structCall);
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
          return new Result.Default(ProjTerm.proj(projectee.wellTyped(), ix), type.subst(subst));
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
          return new Result.Default(LamTerm.make(teleRenamed, access), PiTerm.make(tele, field.result().subst(structSubst)));
        });
      }
      case Expr.Tuple tuple -> {
        var items = tuple.items().map(this::synthesize);
        yield new Result.Default(TupTerm.explicits(items.map(Result::wellTyped)),
          new SigmaTerm(items.map(item -> new Term.Param(LocalVar.IGNORED, item.type(), true))));
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
            // This type decl can be inferred by javac (say, if we use `var` instead of `Term`).
            // However, IntelliJ IDEA is not always able to infer it, and reports error on the usages.
            // So I decided to explicitly specify the type here.
            Term typeSubst = type.subst(subst).normalize(state, NormalizeMode.NF);
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
          state.solve(fTyHole.ref(), pi);
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
          addWithTerm(withTerm, new Result.Default(app, pi));
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
        yield new Result.Default(newApp, pi.body().subst(subst));
      }
      case Expr.Hole hole -> inherit(hole, localCtx.freshTyHole(Constants.randomName(hole), hole.sourcePos())._2);
      case Expr.Error err -> Result.Default.error(err.description());
      case Expr.LitInt lit -> {
        int integer = lit.integer();
        // TODO[literal]: int literals. Currently the parser does not allow negative literals.
        var defs = shapeFactory.findImpl(AyaShape.NAT_SHAPE);
        if (defs.isEmpty()) yield fail(expr, new NoRuleError(expr, null));
        if (defs.sizeGreaterThan(1)) {
          var type = localCtx.freshTyHole("_ty" + lit.integer() + "'", lit.sourcePos());
          yield new Result.Default(new MetaLitTerm(lit.sourcePos(), lit.integer(), defs, type._1), type._1);
        }
        var match = defs.first();
        var type = new DataCall(((DataDef) match._1).ref, 0, ImmutableSeq.empty());
        yield new Result.Default(new IntegerTerm(integer, match._2, type), type);
      }
      case Expr.LitString litStr -> {
        if (!state.primFactory().have(PrimDef.ID.STRING))
          yield fail(expr, new NoRuleError(expr, null));

        yield new Result.Default(new StringTerm(litStr.string()), state.primFactory().getCall(PrimDef.ID.STRING));
      }
      case Expr.Path path -> localCtx.withIntervals(path.params().view(), () -> {
        var type = synthesize(path.type());
        var partial = elaboratePartial(path.partial(), type.wellTyped());
        var cube = new PathTerm(path.params(), type.wellTyped(), partial);
        return new Result.Default(cube, type.type());
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
        yield new Result.Default(new ListTerm(results, match._2, type), type);
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

        yield new Result.Default(full, bodyResult.type());
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
        var typeWHNF = whnf(term);
        if (typeWHNF instanceof MetaTerm hole) yield inheritFallbackUnify(hole, synthesize(expr), expr);
        if (!(typeWHNF instanceof SigmaTerm sigma))
          yield fail(expr, term, BadTypeError.sigmaCon(state, expr, typeWHNF));
        var resultTuple = sigma.check(it, (e, t) -> inherit(e, t).wellTyped());
        if (resultTuple == null)
          yield fail(expr, term, new TupleError.ElemMismatchError(pos, sigma.params().size(), it.size()));
        yield new Result.Default(resultTuple, term);
      }
      case Expr.Hole hole -> {
        // TODO[ice]: deal with unit type
        var freshHole = localCtx.freshHole(term, Constants.randomName(hole), hole.sourcePos());
        if (hole.explicit()) reporter.report(new Goal(state, freshHole._1, hole.accessibleLocal().get()));
        yield new Result.Default(freshHole._2, term);
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
            if (unifyTyReported(result, type, lamParam))
              type = result;
            else yield error(lam, dt);
            addWithTerm(param, type);
            var resultParam = new Term.Param(var, type, param.explicit());
            var body = dt.substBody(resultParam.toTerm());
            yield localCtx.with(resultParam, () -> {
              var rec = check(lam.body(), body);
              return new Result.Default(new LamTerm(resultParam, rec.wellTyped()), dt);
            });
          }
          // Path lambda!
          case PathTerm path -> checkBoundaries(expr, path, new Subst(),
            inherit(expr, path.computePi()).wellTyped());
          default -> fail(lam, term, new BadExprError(lam, term));
        };
      }
      case Expr.LitInt(var pos, var end) -> {
        var ty = whnf(term);
        if (ty instanceof IntervalTerm) {
          if (end == 0 || end == 1) yield new Result.Default(end == 0 ? FormulaTerm.LEFT : FormulaTerm.RIGHT, ty);
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
        yield new Result.Default(new PartialTerm(partial, rhsType), ty);
      }
      case Expr.Match match -> {
        var discriminant = match.discriminant().map(this::synthesize);
        var sig = new Def.Signature<>(discriminant.map(r -> new Term.Param(new LocalVar("_"), r.type(), true)), term);
        var result = PatTycker.elabClausesClassified(this, match.clauses(), sig, match.sourcePos());
        yield new Result.Default(new MatchTerm(discriminant.map(Result::wellTyped), result.matchings()), term);
      }
      default -> inheritFallbackUnify(term, synthesize(expr), expr);
    };
  }

  private @NotNull Term doTy(@NotNull Expr expr) {
    return switch (expr) {
      case Expr.Hole hole -> {
        var freshHole = localCtx.freshTyHole(Constants.randomName(hole), hole.sourcePos());
        if (hole.explicit()) reporter.report(new Goal(state, freshHole._1, hole.accessibleLocal().get()));
        yield freshHole._2;
      }
      case Expr.Sort sort -> new SortTerm(sort.kind(), sort.lift());
      case Expr.Pi pi -> {
        var param = pi.param();
        final var var = param.ref();
        var domRes = ty(param.type());
        addWithTerm(param, domRes);
        var resultParam = new Term.Param(var, domRes, param.explicit());
        yield localCtx.with(resultParam, () -> new PiTerm(resultParam, ty(pi.last())));
      }
      case Expr.Sigma sigma -> {
        var resultTele = MutableList.<Tuple3<LocalVar, Boolean, Term>>create();
        for (var tuple : sigma.params()) {
          var result = ty(tuple.type());
          addWithTerm(tuple, result);
          var ref = tuple.ref();
          localCtx.put(ref, result);
          resultTele.append(Tuple.of(ref, tuple.explicit(), result));
        }
        localCtx.remove(sigma.params().view().map(Expr.Param::ref));
        yield new SigmaTerm(Term.Param.fromBuffer(resultTele));
      }
      default -> synthesize(expr).wellTyped();
    };
  }

  public @NotNull Result.Sort sort(@NotNull Expr expr) {
    return new Result.Sort(sort(expr, ty(expr)));
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

  public ExprTycker(@NotNull PrimDef.Factory primFactory, @NotNull AyaShape.Factory shapeFactory, @NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder) {
    super(reporter, traceBuilder, new TyckState(primFactory));
    this.shapeFactory = shapeFactory;
  }

  public @NotNull Result inherit(@NotNull Expr expr, @NotNull Term type) {
    return traced(() -> new Trace.ExprT(expr, type.freezeHoles(state)), expr, e -> {
      if (type instanceof PiTerm pi && !pi.param().explicit() && needImplicitParamIns(e)) {
        var implicitParam = new Term.Param(new LocalVar(Constants.ANONYMOUS_PREFIX), pi.param().type(), false);
        var body = localCtx.with(implicitParam, () -> inherit(e, pi.substBody(implicitParam.toTerm()))).wellTyped();
        return new Result.Default(new LamTerm(implicitParam, body), pi);
      } else return doInherit(e, type);
    });
  }

  public @NotNull Result synthesize(@NotNull Expr expr) {
    return traced(() -> new Trace.ExprT(expr, null), expr, this::doSynthesize);
  }

  public @NotNull Term ty(@NotNull Expr expr) {
    return traced(() -> new Trace.ExprT(expr, null), () -> doTy(expr));
  }

  private static boolean needImplicitParamIns(@NotNull Expr expr) {
    return expr instanceof Expr.Lambda ex && ex.param().explicit() || !(expr instanceof Expr.Lambda);
  }

  /// TODO: This function should be rewritten.
  public @NotNull Result check(@NotNull Expr expr, @NotNull Term type) {
    if (type instanceof MetaTerm) {
      var synthesis = synthesize(expr);
      unifyTyReported(type, synthesis.type(), expr);
      return synthesis;
    }
    return withInProp(isPropType(type), () -> inherit(expr, type));
  }
}

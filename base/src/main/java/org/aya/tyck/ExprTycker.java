// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableMap;
import kala.control.Either;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Tuple3;
import org.aya.api.distill.AyaDocile;
import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.PreLevelVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.api.util.InternalException;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Signatured;
import org.aya.core.def.*;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.core.visitor.Unfolder;
import org.aya.generic.Level;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.error.*;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.DefEq;
import org.aya.tyck.unify.EqnSet;
import org.aya.tyck.unify.level.LevelEqnSet;
import org.aya.util.Constants;
import org.aya.util.Ordering;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

import static org.aya.util.Constants.ANONYMOUS_PREFIX;

/**
 * @apiNote make sure to instantiate this class once for each {@link Decl}.
 * Do <em>not</em> use multiple instances in the tycking of one {@link Decl}
 * and do <em>not</em> reuse instances of this class in the tycking of multiple {@link Decl}s.
 */
public final class ExprTycker {
  public final @NotNull Reporter reporter;
  public @NotNull LocalCtx localCtx = new LocalCtx();
  public final @Nullable Trace.Builder traceBuilder;
  public final @NotNull LevelEqnSet levelEqns = new LevelEqnSet();
  public final @NotNull EqnSet termEqns = new EqnSet();
  public final @NotNull Sort.LvlVar universe = new Sort.LvlVar("u", null);
  public final @NotNull MutableMap<PreLevelVar, Sort.LvlVar> levelMapping = MutableMap.create();

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  private @NotNull Result doSynthesize(@NotNull Expr expr) {
    return switch (expr) {
      case Expr.LamExpr lam -> inherit(lam, generatePi(lam));
      case Expr.UnivExpr univ -> {
        var sort = transformLevel(univ.level());
        yield new Result(new FormTerm.Univ(sort), new FormTerm.Univ(sort.lift(1)));
      }
      case Expr.RefExpr ref -> switch (ref.resolvedVar()) {
        case LocalVar loc -> {
          var ty = localCtx.get(loc);
          yield new Result(new RefTerm(loc, ty), ty);
        }
        case DefVar<?, ?> defVar -> inferRef(ref.sourcePos(), defVar);
        default -> throw new IllegalStateException("Unknown var: " + ref.resolvedVar().getClass());
      };
      case Expr.PiExpr pi -> inherit(pi, FormTerm.Univ.OMEGA);
      case Expr.SigmaExpr sigma -> inherit(sigma, FormTerm.Univ.OMEGA);
      case Expr.NewExpr newExpr -> {
        var struct = synthesize(newExpr.struct()).wellTyped;
        while (struct.normalize(NormalizeMode.WHNF) instanceof IntroTerm.Lambda intro && !intro.param().explicit()) {
          var holeApp = mockTerm(intro.param(), newExpr.struct().sourcePos());
          struct = CallTerm.make(intro, new Arg<>(holeApp, false));
        }
        if (!(struct instanceof CallTerm.Struct structCall))
          yield fail(newExpr.struct(), struct, BadTypeError.structCon(newExpr, struct));
        var structRef = structCall.ref();

        var subst = new Substituter.TermSubst(MutableMap.from(
          Def.defTele(structRef).view().zip(structCall.args())
            .map(t -> Tuple.of(t._1.ref(), t._2.term()))));
        var levelSubst = new LevelSubst.Simple(MutableMap.from(
          Def.defLevels(structRef).view().zip(structCall.sortArgs())));

        var fields = Buffer.<Tuple2<DefVar<FieldDef, Decl.StructField>, Term>>create();
        var missing = Buffer.<Var>create();
        var conFields = newExpr.fields();

        for (var defField : structRef.core.fields) {
          var conFieldOpt = conFields.find(t -> t.name().equals(defField.ref().name()));
          if (conFieldOpt.isEmpty()) {
            if (defField.body.isEmpty())
              missing.append(defField.ref()); // no value available, skip and prepare error reporting
            else {
              // use default value from defField
              var field = defField.body.get().subst(subst, levelSubst);
              fields.append(Tuple.of(defField.ref(), field));
              subst.add(defField.ref(), field);
            }
            continue;
          }
          var conField = conFieldOpt.get();
          conFields = conFields.dropWhile(t -> t == conField);
          var type = Def.defType(defField.ref()).subst(subst, levelSubst);
          var telescope = defField.ref().core.selfTele.map(term -> term.subst(subst, levelSubst));
          var bindings = conField.bindings();
          if (telescope.sizeLessThan(bindings.size())) {
            // TODO[ice]: number of args don't match
            throw new TyckerException();
          }
          var fieldExpr = bindings.zip(telescope).foldRight(conField.body(), (pair, lamExpr) ->
            new Expr.LamExpr(conField.body().sourcePos(), new Expr.Param(pair._1.sourcePos(), pair._1.data(), pair._2.explicit()), lamExpr));
          var field = inherit(fieldExpr, type).wellTyped;
          fields.append(Tuple.of(defField.ref(), field));
          subst.add(defField.ref(), field);
        }

        if (missing.isNotEmpty())
          yield fail(newExpr, structCall, new FieldProblem.MissingFieldError(newExpr.sourcePos(), missing.toImmutableSeq()));
        if (conFields.isNotEmpty())
          yield fail(newExpr, structCall, new FieldProblem.NoSuchFieldError(newExpr.sourcePos(), conFields.map(Expr.Field::name)));
        yield new Result(new IntroTerm.New(structCall, ImmutableMap.from(fields)), structCall);
      }
      case Expr.ProjExpr proj -> {
        var struct = proj.tup();
        var projectee = synthesize(struct);
        var whnf = projectee.type.normalize(NormalizeMode.WHNF);
        yield proj.ix().fold(ix -> {
            if (!(whnf instanceof FormTerm.Sigma sigma))
              return fail(struct, whnf, BadTypeError.sigmaAcc(struct, ix, whnf));
            var telescope = sigma.params();
            var index = ix - 1;
            if (index < 0 || index >= telescope.size())
              return fail(proj, ErrorTerm.typeOf(proj), new ProjIxError(proj, ix, telescope.size()));
            var type = telescope.get(index).type();
            var subst = ElimTerm.Proj.projSubst(projectee.wellTyped, index, telescope);
            return new Result(new ElimTerm.Proj(projectee.wellTyped, ix), type.subst(subst));
          }, sp -> {
            var fieldName = sp.data();
            if (!(whnf instanceof CallTerm.Struct structCall))
              return fail(struct, ErrorTerm.unexpected(whnf), BadTypeError.structAcc(struct, fieldName, whnf));
            var structCore = structCall.ref().core;
            if (structCore == null) throw new UnsupportedOperationException("TODO");
            var projected = structCore.fields.find(field -> Objects.equals(field.ref().name(), fieldName));
            if (projected.isEmpty()) {
              // TODO[ice]: field not found
              throw new TyckerException();
            }
            // TODO[ice]: instantiate the type
            var field = projected.get();
            var fieldRef = field.ref();
            proj.resolvedIx().value = fieldRef;

            var structSubst = Unfolder.buildSubst(structCore.telescope(), structCall.args());
            var levels = levelStuffs(struct.sourcePos(), fieldRef);
            var tele = Term.Param.subst(fieldRef.core.selfTele, structSubst, levels._1);
            var teleRenamed = tele.map(Term.Param::rename);
            var access = new CallTerm.Access(projectee.wellTyped, fieldRef, levels._2,
              structCall.args(), teleRenamed.map(Term.Param::toArg));
            return new Result(IntroTerm.Lambda.make(teleRenamed, access),
              FormTerm.Pi.make(tele, field.result().subst(structSubst, levels._1)));
          }
        );
      }
      case Expr.TupExpr tuple -> {
        var items = tuple.items().map(this::synthesize);
        yield new Result(new IntroTerm.Tuple(items.map(Result::wellTyped)),
          new FormTerm.Sigma(items.map(item -> new Term.Param(Constants.anonymous(), item.type, true))));
      }
      case Expr.AppExpr appE -> {
        var f = synthesize(appE.function());
        var app = f.wellTyped;
        var argument = appE.argument();
        var namedArg = argument.term();
        if (namedArg.expr() instanceof Expr.UnivArgsExpr univArgs) {
          univArgs(app, univArgs);
          yield f;
        }
        if (!(f.type.normalize(NormalizeMode.WHNF) instanceof FormTerm.Pi piTerm))
          yield fail(appE, f.type, BadTypeError.pi(appE, f.type));
        var pi = piTerm;
        var subst = new Substituter.TermSubst(MutableMap.create());
        var argLicit = argument.explicit();
        while (pi.param().explicit() != argLicit ||
          namedArg.name() != null && !Objects.equals(pi.param().ref().name(), namedArg.name())) {
          if (argLicit || namedArg.name() != null) {
            // that implies paramLicit == false
            var holeApp = mockTerm(pi.param(), namedArg.expr().sourcePos());
            app = CallTerm.make(app, new Arg<>(holeApp, false));
            var newPi = instPi(pi, subst, holeApp);
            if (newPi.isLeft()) pi = newPi.getLeftValue();
            else yield fail(appE, newPi.getRightValue(), BadTypeError.pi(appE, newPi.getRightValue()));
          } else yield fail(appE, new ErrorTerm(pi.body()),
            new LicitProblem.UnexpectedImplicitArgError(argument));
        }
        var elabArg = inherit(namedArg.expr(), pi.param().type()).wellTyped;
        app = CallTerm.make(app, new Arg<>(elabArg, argLicit));
        subst.map().put(pi.param().ref(), elabArg);
        yield new Result(app, subst.isEmpty() ? pi : pi.body().subst(subst));
      }
      case Expr.HoleExpr hole -> inherit(hole, localCtx.freshHole(
        FormTerm.Univ.OMEGA, Constants.randomName(hole), expr.sourcePos())._2);
      default -> new Result(ErrorTerm.unexpected(expr), new ErrorTerm($ -> Doc.english("no rule"), false));
    };
  }

  private void univArgs(Term app, Expr.UnivArgsExpr univArgs) {
    if (unwrapLambda(app) instanceof CallTerm call) {
      var sortArgs = call.sortArgs();
      var levels = univArgs.univArgs();
      if (sortArgs.sizeEquals(levels)) sortArgs.zipView(levels).forEach(t ->
        levelEqns.add(t._1, transformLevel(t._2), Ordering.Eq, univArgs.sourcePos()));
      else reporter.report(new UnivArgsError.SizeMismatch(univArgs, sortArgs.size()));
    } else reporter.report(new UnivArgsError.Misplaced(univArgs));
  }

  private @NotNull Term unwrapLambda(@NotNull Term term) {
    return term instanceof IntroTerm.Lambda lambda ? unwrapLambda(lambda.body()) : term;
  }

  private @NotNull Result doInherit(@NotNull Expr expr, @NotNull Term term) {
    return switch (expr) {
      case Expr.TupExpr tuple -> {
        var items = Buffer.<Term>create();
        var resultTele = Buffer.<Term.@NotNull Param>create();
        var typeWHNF = term.normalize(NormalizeMode.WHNF);
        if (typeWHNF instanceof CallTerm.Hole hole) yield unifyTyMaybeInsert(hole, synthesize(tuple), tuple);
        if (!(typeWHNF instanceof FormTerm.Sigma dt))
          yield fail(tuple, term, BadTypeError.sigmaCon(tuple, term));
        var againstTele = dt.params().view();
        var last = dt.params().last().type();
        for (var iter = tuple.items().iterator(); iter.hasNext(); ) {
          var item = iter.next();
          var result = inherit(item, againstTele.first().type());
          items.append(result.wellTyped);
          var ref = againstTele.first().ref();
          resultTele.append(new Term.Param(ref, result.type, againstTele.first().explicit()));
          againstTele = againstTele.drop(1);
          if (againstTele.isNotEmpty()) {
            var subst = new Substituter.TermSubst(ref, result.wellTyped);
            againstTele = againstTele.map(param -> param.subst(subst)).toSeq().view();
            last = last.subst(subst);
          } else {
            if (iter.hasNext()) {
              // TODO[ice]: too few tuple elements
              throw new TyckerException();
            } else items.append(inherit(item, last).wellTyped);
          }
        }
        var resTy = new FormTerm.Sigma(resultTele.toImmutableSeq());
        yield new Result(new IntroTerm.Tuple(items.toImmutableSeq()), resTy);
      }
      case Expr.HoleExpr hole -> {
        // TODO[ice]: deal with unit type
        var freshHole = localCtx.freshHole(term, Constants.randomName(hole), hole.sourcePos());
        if (hole.explicit()) reporter.report(new Goal(freshHole._1, hole.accessibleLocal().value));
        yield new Result(freshHole._2, term);
      }
      case Expr.UnivExpr univExpr -> {
        var sort = transformLevel(univExpr.level());
        var normTerm = term.normalize(NormalizeMode.WHNF);
        if (normTerm instanceof FormTerm.Univ univ) {
          levelEqns.add(sort.lift(1), univ.sort(), Ordering.Lt, univExpr.sourcePos());
          yield new Result(new FormTerm.Univ(sort), univ);
        } else {
          var succ = new FormTerm.Univ(sort.lift(1));
          unifyTyReported(normTerm, succ, univExpr);
        }
        yield new Result(new FormTerm.Univ(sort), term);
      }
      case Expr.LamExpr lam -> {
        if (term instanceof CallTerm.Hole) unifyTy(term, generatePi(lam), lam.sourcePos());
        if (!(term.normalize(NormalizeMode.WHNF) instanceof FormTerm.Pi dt)) {
          yield fail(lam, term, BadTypeError.pi(lam, term));
        }
        var param = lam.param();
        if (param.explicit() != dt.param().explicit()) {
          yield fail(lam, dt, new LicitProblem.LicitMismatchError(lam, dt));
        }
        var var = param.ref();
        var lamParam = param.type();
        var type = dt.param().type();
        if (lamParam != null) {
          var result = inherit(lamParam, FormTerm.Univ.OMEGA);
          var comparison = unifyTy(result.wellTyped, type, lamParam.sourcePos());
          if (!comparison) {
            // TODO[ice]: expected type mismatch lambda type annotation
            throw new TyckerException();
          } else type = result.wellTyped;
        }
        var resultParam = new Term.Param(var, type, param.explicit());
        yield localCtx.with(resultParam, () -> {
          var body = dt.substBody(resultParam.toTerm());
          var rec = inherit(lam.body(), body);
          return new Result(new IntroTerm.Lambda(resultParam, rec.wellTyped), dt);
        });
      }
      case Expr.PiExpr pi -> {
        var param = pi.param();
        final var var = param.ref();
        var type = param.type();
        if (type == null) type = new Expr.HoleExpr(param.sourcePos(), false, null);
        var result = inherit(type, term);
        var resultParam = new Term.Param(var, result.wellTyped, param.explicit());
        yield localCtx.with(resultParam, () -> {
          var last = inherit(pi.last(), term);
          return new Result(new FormTerm.Pi(resultParam, last.wellTyped), term);
        });
      }
      case Expr.SigmaExpr sigma -> {
        var resultTele = Buffer.<Tuple3<LocalVar, Boolean, Term>>create();
        sigma.params().forEach(tuple -> {
          final var type = tuple.type();
          if (type == null) {
            // TODO[ice]: report error or generate meta?
            //  I guess probably report error for now.
            throw new TyckerException();
          }
          var result = inherit(type, term);
          var ref = tuple.ref();
          localCtx.put(ref, result.wellTyped);
          resultTele.append(Tuple.of(ref, tuple.explicit(), result.wellTyped));
        });
        sigma.params().view()
          .map(Expr.Param::ref)
          .forEach(localCtx.localMap()::remove);
        yield new Result(new FormTerm.Sigma(Term.Param.fromBuffer(resultTele)), term);
      }
      default -> unifyTyMaybeInsert(term, synthesize(expr), expr);
    };
  }

  public @NotNull ImmutableSeq<Sort.LvlVar> extractLevels() {
    return Seq.of(universe).view()
      .filter(levelEqns::used)
      .appendedAll(levelMapping.valuesView())
      .toImmutableSeq();
  }

  private void traceExit(Result result, @NotNull Expr expr) {
    tracing(builder -> {
      builder.append(new Trace.TyckT(result.wellTyped.freezeHoles(levelEqns), result.type.freezeHoles(levelEqns), expr.sourcePos()));
      builder.reduce();
    });
    // assert validate(result.wellTyped);
    // assert validate(result.type);
    if (expr instanceof Expr.WithTerm withTerm) withTerm.theCore().set(result.wellTyped);
  }

  /*
  @TestOnly @Contract(pure = true) private boolean validate(Term term) {
    var visitor = new TermConsumer<Unit>() {
      boolean ok = true;

      @Override public void visitSort(@NotNull Sort sort, Unit unit) {
        for (var level : sort.levels())
          if (level instanceof Level.Reference<Sort.LvlVar> r && !r.ref().free() &&
            !(r.ref() == universe || levelMapping.valuesView().contains(r.ref())))
            ok = false;
      }
    };
    term.accept(visitor, Unit.unit());
    return visitor.ok;
  }
  */

  public ExprTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder) {
    this.reporter = reporter;
    this.traceBuilder = traceBuilder;
  }

  public void solveMetas() {
    while (termEqns.eqns().isNotEmpty()) {
      //noinspection StatementWithEmptyBody
      while (termEqns.simplify(levelEqns, reporter, traceBuilder)) ;
      // If the standard 'pattern' fragment cannot solve all equations, try to use a nonstandard method
      var eqns = termEqns.eqns().toImmutableSeq();
      if (eqns.isNotEmpty()) {
        for (var eqn : eqns) termEqns.solveEqn(levelEqns, reporter, traceBuilder, eqn, true);
        reporter.report(new HoleProblem.CannotFindGeneralSolution(eqns));
      }
    }
    levelEqns.solve();
  }

  public @NotNull Result inherit(@NotNull Expr expr, @NotNull Term type) {
    tracing(builder -> builder.shift(new Trace.ExprT(expr, type.freezeHoles(levelEqns))));
    Result result;
    if (type instanceof FormTerm.Pi pi && needImplicitParamIns(expr, pi)) {
      var implicitParam = new Term.Param(new LocalVar(ANONYMOUS_PREFIX), pi.param().type(), false);
      var body = localCtx.with(implicitParam, () ->
        inherit(expr, pi.substBody(implicitParam.toTerm()))).wellTyped;
      result = new Result(new IntroTerm.Lambda(implicitParam, body), pi);
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

  private static boolean needImplicitParamIns(@NotNull Expr expr, @NotNull FormTerm.Pi type) {
    return !type.param().explicit()
      && (expr instanceof Expr.LamExpr ex && ex.param().explicit()
      || !(expr instanceof Expr.LamExpr));
  }

  public @NotNull Result zonk(@NotNull Expr expr, @NotNull Result result) {
    solveMetas();
    var pos = expr.sourcePos();
    return new Result(result.wellTyped.zonk(this, pos), result.type.zonk(this, pos));
  }

  private @NotNull Term generatePi(Expr.@NotNull LamExpr expr) {
    var param = expr.param();
    var sourcePos = param.sourcePos();
    var domain = localCtx.freshHole(FormTerm.Univ.OMEGA,
      param.ref().name() + Constants.GENERATED_POSTFIX, sourcePos)._2;
    var codomain = localCtx.freshHole(FormTerm.Univ.OMEGA, sourcePos)._2;
    return new FormTerm.Pi(Term.Param.mock(domain, param), codomain);
  }

  private @NotNull Result fail(@NotNull AyaDocile expr, @NotNull Term term, @NotNull Problem prob) {
    reporter.report(prob);
    return new Result(new ErrorTerm(expr), term);
  }

  private @NotNull Sort transformLevel(@NotNull Level<PreLevelVar> level) {
    if (level instanceof Level.Polymorphic) return levelEqns.markUsed(universe);
    if (level instanceof Level.Maximum m)
      return Sort.merge(m.among().map(this::transformLevel));
    return new Sort(switch (level) {
      case Level.Reference<PreLevelVar> v -> {
        var ref = v.ref();
        var lvlVar = ref.sourcePos() != null ? new Sort.LvlVar(ref.name(), ref.sourcePos())
          : levelMapping.getOrPut(ref, () -> new Sort.LvlVar(ref.name(), null));
        yield new Level.Reference<>(lvlVar, v.lift());
      }
      case Level.Infinity<PreLevelVar> l -> new Level.Infinity<>();
      case Level.Constant<PreLevelVar> c -> new Level.Constant<>(c.value());
      default -> throw new IllegalArgumentException(level.toString());
    });
  }

  @SuppressWarnings("unchecked")
  private @NotNull Result inferRef(@NotNull SourcePos pos, @NotNull DefVar<?, ?> var) {
    if (var.core instanceof FnDef || var.concrete instanceof Decl.FnDecl) {
      return defCall(pos, (DefVar<FnDef, Decl.FnDecl>) var, CallTerm.Fn::new);
    } else if (var.core instanceof PrimDef) {
      return defCall(pos, (DefVar<PrimDef, Decl.PrimDecl>) var, CallTerm.Prim::new);
    } else if (var.core instanceof DataDef || var.concrete instanceof Decl.DataDecl) {
      return defCall(pos, (DefVar<DataDef, Decl.DataDecl>) var, CallTerm.Data::new);
    } else if (var.core instanceof StructDef || var.concrete instanceof Decl.StructDecl) {
      return defCall(pos, (DefVar<StructDef, Decl.StructDecl>) var, CallTerm.Struct::new);
    } else if (var.core instanceof CtorDef || var.concrete instanceof Decl.DataDecl.DataCtor) {
      var conVar = (DefVar<CtorDef, Decl.DataDecl.DataCtor>) var;
      var level = levelStuffs(pos, conVar);
      var tele = Term.Param.subst(Def.defTele(conVar), level._1);
      var type = FormTerm.Pi.make(tele, Def.defResult(conVar).subst(Substituter.TermSubst.EMPTY, level._1));
      var telescopes = CtorDef.telescopes(conVar, level._2).rename();
      var body = telescopes.toConCall(conVar).subst(Substituter.TermSubst.EMPTY, level._1);
      return new Result(IntroTerm.Lambda.make(telescopes.params(), body), type);
    } else if (var.core instanceof FieldDef || var.concrete instanceof Decl.StructField) {
      // the code runs to here because we are tycking a StructField in a StructDecl
      // there should be two-stage check for this case:
      //  - check the definition's correctness: happens here
      //  - check the field value's correctness: happens in `visitNew` after the body was instantiated
      var field = (DefVar<FieldDef, Decl.StructField>) var;
      return new Result(new RefTerm.Field(field), Def.defType(field));
    } else {
      final var msg = "Def var `" + var.name() + "` has core `" + var.core + "` which we don't know.";
      throw new IllegalStateException(msg);
    }
  }

  private @NotNull <D extends Def, S extends Signatured> ExprTycker.Result
  defCall(@NotNull SourcePos pos, DefVar<D, S> defVar, CallTerm.Factory<D, S> function) {
    var level = levelStuffs(pos, defVar);
    var tele = Term.Param.subst(Def.defTele(defVar), level._1);
    var teleRenamed = tele.map(Term.Param::rename);
    // unbound these abstracted variables
    var body = function.make(defVar, level._2, teleRenamed.map(Term.Param::toArg));
    var type = FormTerm.Pi.make(tele, Def.defResult(defVar).subst(Substituter.TermSubst.EMPTY, level._1));
    return new Result(IntroTerm.Lambda.make(teleRenamed, body), type);
  }

  private @NotNull Tuple2<LevelSubst.Simple, ImmutableSeq<Sort>>
  levelStuffs(@NotNull SourcePos pos, DefVar<? extends Def, ? extends Signatured> defVar) {
    var levelSubst = new LevelSubst.Simple(MutableMap.create());
    var levelVars = Def.defLevels(defVar).map(v -> {
      var lvlVar = new Sort.LvlVar(defVar.name() + "." + v.name(), pos);
      levelSubst.solution().put(v, new Sort(new Level.Reference<>(lvlVar)));
      return lvlVar;
    });
    levelEqns.vars().appendAll(levelVars);
    return Tuple.of(levelSubst, levelVars.view()
      .map(Level.Reference::new)
      .map(Sort::new)
      .toImmutableSeq());
  }

  private boolean unifyTy(@NotNull Term upper, @NotNull Term lower, @NotNull SourcePos pos) {
    tracing(builder -> builder.append(new Trace.UnifyT(lower, upper, pos)));
    return unifier(pos, Ordering.Lt).compare(lower, upper, FormTerm.Univ.OMEGA);
  }

  public @NotNull DefEq unifier(@NotNull SourcePos pos, @NotNull Ordering ord) {
    return new DefEq(ord, reporter, false, levelEqns, termEqns, traceBuilder, pos);
  }

  /**
   * Check if <code>lower</code> is a subtype of <code>upper</code>,
   * and report a type error if it's not the case.
   *
   * @see ExprTycker#unifyTyMaybeInsert(Term, Result, Expr)
   */
  void unifyTyReported(@NotNull Term upper, @NotNull Term lower, Expr loc) {
    var unification = unifyTy(upper, lower, loc.sourcePos());
    if (!unification) reporter.report(new UnifyError(loc, upper, lower));
  }

  /**
   * Check if <code>lower</code> is a subtype of <code>upper</code>,
   * and try to insert implicit arguments to fulfill this goal (if possible).
   *
   * @return the term and type after insertion
   * @see ExprTycker#unifyTyReported(Term, Term, Expr)
   */
  private Result unifyTyMaybeInsert(@NotNull Term upper, @NotNull Result result, Expr loc) {
    var lower = result.type;
    var term = result.wellTyped;
    while (lower.normalize(NormalizeMode.WHNF) instanceof FormTerm.Pi pi && !pi.param().explicit()) {
      var mock = mockTerm(pi.param(), loc.sourcePos());
      term = CallTerm.make(term, new Arg<>(mock, false));
      lower = pi.substBody(mock);
    }
    if (unifyTy(upper, lower, loc.sourcePos())) return new Result(term, lower);
    return fail(term.freezeHoles(levelEqns), upper, new UnifyError(loc, upper, lower));
  }

  private @NotNull Term mockTerm(Term.Param param, SourcePos pos) {
    // TODO: maybe we should create a concrete hole and check it against the type
    //  in case we can synthesize this term via its type only
    var genName = param.ref().name().concat(Constants.GENERATED_POSTFIX);
    return localCtx.freshHole(param.type(), genName, pos)._2;
  }

  private Either<FormTerm.Pi, Term>
  instPi(@NotNull FormTerm.Pi pi, Substituter.TermSubst subst, @NotNull Term arg) {
    subst.add(pi.param().ref(), arg);
    var term = pi.body().subst(subst).normalize(NormalizeMode.WHNF);
    return term instanceof FormTerm.Pi pai ? Either.left(pai) : Either.right(term);
  }

  public static class TyckerException extends InternalException {
    @Override public void printHint() {
      System.err.println("A type error was discovered during type checking.");
    }

    @Override public int exitCode() {
      return 2;
    }
  }

  /**
   * {@link Result#type} is the type of {@link Result#wellTyped}.
   *
   * @author ice1000
   */
  public record Result(@NotNull Term wellTyped, @NotNull Term type) {
    @Contract(value = " -> new", pure = true) public @NotNull Tuple2<Term, Term> toTuple() {
      return Tuple.of(type, wellTyped);
    }
  }
}

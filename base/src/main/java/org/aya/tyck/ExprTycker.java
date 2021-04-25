// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LevelGenVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.api.util.InternalException;
import org.aya.api.util.InterruptException;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Decl;
import org.aya.concrete.Expr;
import org.aya.concrete.Signatured;
import org.aya.concrete.visitor.ExprRefSubst;
import org.aya.core.def.*;
import org.aya.core.sort.LevelEqnSet;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.core.visitor.Unfolder;
import org.aya.generic.Level;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.error.NoSuchFieldError;
import org.aya.tyck.error.*;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.Rule;
import org.aya.tyck.unify.TypedDefEq;
import org.aya.util.Constants;
import org.aya.util.Ordering;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.immutable.ImmutableMap;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.tuple.Tuple3;
import org.glavo.kala.tuple.Unit;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * @apiNote make sure to instantiate this class once for each {@link Decl}.
 * Do <em>not</em> use multiple instances in the tycking of one {@link Decl}
 * and do <em>not</em> reuse instances of this class in the tycking of multiple {@link Decl}s.
 */
public class ExprTycker implements Expr.BaseVisitor<Term, ExprTycker.Result> {
  public final @NotNull Reporter reporter;
  public @NotNull LocalCtx localCtx;
  public final @Nullable Trace.Builder traceBuilder;
  public final @NotNull LevelEqnSet equations;
  private final @NotNull Sort.LvlVar homotopy = new Sort.LvlVar("h", LevelGenVar.Kind.Homotopy, null);
  private final @NotNull Sort.LvlVar universe = new Sort.LvlVar("u", LevelGenVar.Kind.Universe, null);
  public final @NotNull MutableMap<LevelGenVar, Sort.LvlVar> levelMapping = MutableMap.of();

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  @Override public void traceEntrance(@NotNull Expr expr, Term term) {
    tracing(builder -> builder.shift(new Trace.ExprT(expr, term)));
  }

  public @NotNull ImmutableSeq<Sort.LvlVar> extractLevels() {
    return Seq.of(homotopy, universe).view()
      .filter(equations::constraints)
      .appendedAll(levelMapping.valuesView())
      .toImmutableSeq();
  }

  @Override public void traceExit(Result result, @NotNull Expr expr, Term p) {
    tracing(builder -> {
      builder.shift(new Trace.TyckT(result.wellTyped, result.type, expr.sourcePos()));
      builder.reduce();
      builder.reduce();
    });
  }

  public ExprTycker(@NotNull Reporter reporter, @NotNull LocalCtx localCtx, Trace.@Nullable Builder traceBuilder) {
    this.reporter = reporter;
    this.localCtx = localCtx;
    this.traceBuilder = traceBuilder;
    equations = new LevelEqnSet(reporter);
  }

  public ExprTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder) {
    this(reporter, new LocalCtx(), traceBuilder);
  }

  public @NotNull Result finalize(@NotNull Result result) {
    equations.solve();
    return new Result(result.wellTyped.zonk(this), result.type.zonk(this));
  }

  public @NotNull Result checkNoZonk(@NotNull Expr expr, @Nullable Term type) {
    return expr.accept(this, type);
  }

  public @NotNull Result checkExpr(@NotNull Expr expr, @Nullable Term type) {
    return finalize(checkNoZonk(expr, type));
  }

  @Rule.Check(partialSynth = true)
  @Override public Result visitLam(Expr.@NotNull LamExpr expr, @Nullable Term term) {
    if (term == null) {
      var sourcePos = expr.param().sourcePos();
      var domain = localCtx.freshHole(FormTerm.Univ.OMEGA, Constants.ANONYMOUS_PREFIX, sourcePos)._2;
      var codomain = localCtx.freshHole(FormTerm.Univ.OMEGA, Constants.ANONYMOUS_PREFIX, sourcePos)._2;
      term = new FormTerm.Pi(false, Term.Param.mock(domain, expr.param().explicit()), codomain);
    }
    if (!(term.normalize(NormalizeMode.WHNF) instanceof FormTerm.Pi dt && !dt.co())) {
      return wantButNo(expr, term, "pi type");
    }
    var param = expr.param();
    var var = param.ref();
    var lamParam = param.type();
    var type = dt.param().type();
    if (lamParam != null) {
      var result = lamParam.accept(this, FormTerm.Univ.OMEGA);
      var comparison = unifyTy(result.wellTyped, type, lamParam.sourcePos());
      if (!comparison) {
        // TODO[ice]: expected type mismatch lambda type annotation
        throw new TyckerException();
      } else type = result.wellTyped;
    }
    var resultParam = new Term.Param(var, type, param.explicit());
    return localCtx.with(resultParam, () -> {
      var body = dt.substBody(new RefTerm(var));
      var rec = expr.body().accept(this, body);
      return new Result(new IntroTerm.Lambda(resultParam, rec.wellTyped), dt);
    });
  }

  <T> T wantButNo(@NotNull Expr expr, Term term, String expectedText) {
    reporter.report(new BadTypeError(expr, Doc.plain(expectedText), term));
    throw new TyckInterruptedException();
  }

  private @NotNull Level<Sort.LvlVar> transformLevel(@NotNull Level<LevelGenVar> level) {
    return level.map(v -> levelMapping.getOrPut(v, () -> new Sort.LvlVar(v.name(), v.kind(), null)));
  }

  @Rule.Synth @Override public Result visitUniv(Expr.@NotNull UnivExpr expr, @Nullable Term term) {
    var u = transformLevel(expr.uLevel());
    if (u instanceof Level.Polymorphic<Sort.LvlVar>) u = equations.markUsed(universe);
    var h = transformLevel(expr.hLevel());
    if (h instanceof Level.Polymorphic<Sort.LvlVar>) h = equations.markUsed(homotopy);
    var sort = new Sort(u, h);
    if (term == null) return new Result(new FormTerm.Univ(sort), new FormTerm.Univ(sort.succ(1)));
    var normTerm = term.normalize(NormalizeMode.WHNF);
    if (normTerm instanceof FormTerm.Univ univ) {
      equations.add(sort.succ(1), univ.sort(), Ordering.Lt, expr.sourcePos());
      return new Result(new FormTerm.Univ(sort), univ);
    } else {
      var succ = new FormTerm.Univ(sort.succ(1));
      unifyTyThrowing(normTerm, succ, expr);
      return new Result(new FormTerm.Univ(sort), succ);
    }
  }

  @Rule.Synth @Override public Result visitRef(Expr.@NotNull RefExpr expr, @Nullable Term term) {
    var var = expr.resolvedVar();
    if (var instanceof LocalVar loc) {
      var ty = localCtx.get(loc);
      return refResult(expr, term, ty, new RefTerm(loc));
    } else if (var instanceof DefVar<?, ?> defVar) {
      var result = inferRef(expr.sourcePos(), defVar, term);
      return refResult(expr, term, result.type, result.wellTyped);
    } else throw new IllegalStateException("TODO: UnivVar not yet implemented");
  }

  @SuppressWarnings("unchecked")
  public @NotNull Result inferRef(@NotNull SourcePos pos, @NotNull DefVar<?, ?> var, Term expected) {
    if (var.core instanceof FnDef || var.concrete instanceof Decl.FnDecl) {
      return defCall(pos, (DefVar<FnDef, Decl.FnDecl>) var, CallTerm.Fn::new);
    } else if (var.core instanceof PrimDef) {
      return defCall(pos, (DefVar<PrimDef, Decl.PrimDecl>) var, (v, ca, sorts, args) -> new CallTerm.Prim(v, args, sorts));
    } else if (var.core instanceof DataDef || var.concrete instanceof Decl.DataDecl) {
      return defCall(pos, (DefVar<DataDef, Decl.DataDecl>) var, CallTerm.Data::new);
    } else if (var.core instanceof StructDef || var.concrete instanceof Decl.StructDecl) {
      return defCall(pos, (DefVar<StructDef, Decl.StructDecl>) var, CallTerm.Struct::new);
    } else if (var.core instanceof DataDef.Ctor || var.concrete instanceof Decl.DataDecl.DataCtor) {
      var conVar = (DefVar<DataDef.Ctor, Decl.DataDecl.DataCtor>) var;
      var level = levelStuffs(pos, conVar);
      var telescopes = DataDef.Ctor.telescopes(conVar, level._2);
      var tele = Term.Param.subst(Def.defTele(conVar), level._1);
      var type = FormTerm.Pi.make(false, tele, Def.defResult(conVar).subst(Substituter.TermSubst.EMPTY, level._1));
      return new Result(IntroTerm.Lambda.make(tele, telescopes.toConCall(conVar)), type);
    } else if (var.core instanceof StructDef.Field || var.concrete instanceof Decl.StructField) {
      // the code runs to here because we are tycking a StructField in a StructDecl
      // there should be two-stage check for this case:
      //  - check the definition's correctness: happens here
      //  - check the field value's correctness: happens in `visitNew` after the body was instantiated
      var field = (DefVar<StructDef.Field, Decl.StructField>) var;
      var ty = Def.defResult(field);
      var refExpr = new Expr.RefExpr(field.concrete.sourcePos(), field, field.concrete.ref.name());
      // TODO[ice]: correct this RefTerm
      return refResult(refExpr, expected, ty, new RefTerm(new LocalVar(field.name())));
    } else {
      final var msg = "Def var `" + var.name() + "` has core `" + var.core + "` which we don't know.";
      throw new IllegalStateException(msg);
    }
  }

  private @NotNull Result refResult(
    Expr.@NotNull RefExpr expr, @Nullable Term expected,
    @NotNull Term ty, Term refTerm
  ) {
    if (expected == null) return new Result(refTerm, ty);
    unifyTyThrowing(expected, ty, expr);
    return new Result(refTerm, ty);
  }

  private @NotNull <D extends Def, S extends Signatured> ExprTycker.Result
  defCall(@NotNull SourcePos pos, DefVar<D, S> defVar, CallTerm.Factory<D, S> function) {
    var level = levelStuffs(pos, defVar);
    var tele = Term.Param.subst(Def.defTele(defVar), level._1);
    var ctxTele = Term.Param.subst(Def.defContextTele(defVar), level._1);
    // unbound these abstracted variables
    // ice: should we rename the vars in this telescope? Probably not.
    var body = function.make(defVar,
      ctxTele.map(Term.Param::toArg),
      level._2,
      tele.map(Term.Param::toArg));
    var type = FormTerm.Pi.make(false, tele, Def.defResult(defVar).subst(Substituter.TermSubst.EMPTY, level._1));
    return new Result(IntroTerm.Lambda.make(tele, body), type);
  }

  private @NotNull Tuple2<LevelSubst.Simple, ImmutableSeq<Level<Sort.LvlVar>>>
  levelStuffs(@NotNull SourcePos pos, DefVar<? extends Def, ? extends Signatured> defVar) {
    var levelSubst = new LevelSubst.Simple(MutableMap.of());
    var levelVars = Def.defLevels(defVar).map(v -> {
      var lvlVar = new Sort.LvlVar(defVar.name() + "." + v.name(), v.kind(), pos);
      levelSubst.solution().put(v, new Level.Reference<>(lvlVar));
      return lvlVar;
    });
    equations.vars().appendAll(levelVars);
    return Tuple.of(levelSubst, levelVars.map(Level.Reference::new));
  }

  private boolean unifyTy(Term upper, Term lower, @NotNull SourcePos pos) {
    tracing(builder -> builder.shift(new Trace.UnifyT(lower, upper, pos)));
    tracing(Trace.Builder::reduce);
    return unifier(pos, Ordering.Lt, localCtx).compare(lower, upper, FormTerm.Univ.OMEGA);
  }

  public @NotNull TypedDefEq unifier(@NotNull SourcePos pos, @NotNull Ordering ord, @NotNull LocalCtx ctx) {
    return new TypedDefEq(ctx, ord, this, pos);
  }

  void unifyTyThrowing(Term upper, Term lower, Expr loc) {
    var unification = unifyTy(upper, lower, loc.sourcePos());
    if (!unification) {
      reporter.report(new UnifyError(loc, upper, lower));
      throw new TyckInterruptedException();
    }
  }

  @Rule.Synth @Override public Result visitPi(Expr.@NotNull PiExpr expr, @Nullable Term term) {
    final var against = term != null ? term : new FormTerm.Univ(Sort.OMEGA);
    var param = expr.param();
    final var var = param.ref();
    var type = param.type();
    if (type == null) type = new Expr.HoleExpr(param.sourcePos(), false, null);
    var result = type.accept(this, against);
    var resultParam = new Term.Param(var, result.wellTyped, param.explicit());
    return localCtx.with(resultParam, () -> {
      var last = expr.last().accept(this, against);
      return new Result(new FormTerm.Pi(expr.co(), resultParam, last.wellTyped), against);
    });
  }

  @Rule.Synth @Override public Result visitSigma(Expr.@NotNull SigmaExpr expr, @Nullable Term term) {
    final var against = term != null ? term : new FormTerm.Univ(Sort.OMEGA);
    var resultTele = Buffer.<Tuple3<LocalVar, Boolean, Term>>of();
    expr.params().forEach(tuple -> {
      final var type = tuple.type();
      if (type == null) {
        // TODO[ice]: report error or generate meta?
        //  I guess probably report error for now.
        throw new TyckerException();
      }
      var result = type.accept(this, against);
      resultTele.append(Tuple.of(tuple.ref(), tuple.explicit(), result.wellTyped));
    });
    return new Result(new FormTerm.Sigma(expr.co(), Term.Param.fromBuffer(resultTele)), against);
  }

  @Rule.Check(partialSynth = true)
  @Override public Result visitNew(Expr.@NotNull NewExpr expr, @Nullable Term term) {
    var struct = expr.struct().accept(this, null).wellTyped;
    if (!(struct instanceof CallTerm.Struct structCall))
      return wantButNo(expr.struct(), struct, "struct type");
    var structRef = structCall.ref();

    var subst = new Substituter.TermSubst(MutableMap.of());
    var structTele = Def.defTele(structRef);
    structTele.view().zip(structCall.args())
      .forEach(t -> subst.add(t._1.ref(), t._2.term()));

    var fields = Buffer.<Tuple2<DefVar<StructDef.Field, Decl.StructField>, Term>>of();
    var missing = Buffer.<String>of();
    var conFields = expr.fields();

    for (var defField : structRef.core.fields()) {
      var conFieldOpt = conFields.find(t -> t.name().equals(defField.ref().name()));
      if (conFieldOpt.isEmpty()) {
        if (defField.body().isEmpty())
          missing.append(defField.ref().name()); // no value available, skip and prepare error reporting
        else {
          // use default value from defField
          var field = defField.body().get().subst(subst);
          fields.append(Tuple.of(defField.ref(), field));
          subst.add(defField.ref(), field);
        }
        continue;
      }
      var conField = conFieldOpt.get();
      conFields = conFields.dropWhile(t -> t == conField);
      var type = Def.defResult(defField.ref()).subst(subst);
      var fieldSubst = new ExprRefSubst(reporter);
      var telescope = defField.ref().core.fieldTele();
      if (!telescope.sizeEquals(conField.bindings().size())) {
        // TODO[ice]: number of args don't match
        throw new TyckerException();
      }
      for (var t : telescope.view().zip(conField.bindings())) fieldSubst.good().put(t._2._2, t._1.ref());
      var field = localCtx.with(telescope, () -> conField.body()
        .accept(fieldSubst, Unit.unit())
        .accept(this, type).wellTyped);
      fields.append(Tuple.of(defField.ref(), field));
      subst.add(defField.ref(), field);
    }

    if (missing.isNotEmpty()) {
      reporter.report(new MissingFieldError(expr.sourcePos(), missing.toImmutableSeq()));
      throw new TyckInterruptedException();
    }
    if (conFields.isNotEmpty()) {
      reporter.report(new NoSuchFieldError(expr.sourcePos(), conFields.map(Expr.Field::name).toImmutableSeq()));
      throw new TyckInterruptedException();
    }

    if (term != null) unifyTyThrowing(term, structCall, expr);
    return new Result(new IntroTerm.New(ImmutableMap.from(fields)), structCall);
  }

  @Rule.Synth @Override public Result visitProj(Expr.@NotNull ProjExpr expr, @Nullable Term term) {
    var from = expr.tup();
    var projectee = from.accept(this, null);
    var result = expr.ix().fold(
      ix -> visitProj(from, ix, projectee),
      sp -> visitAccess(from, sp, projectee)
    );
    if (term != null) unifyTyThrowing(term, result.type, expr);
    return result;
  }

  private Result visitAccess(Expr struct, String fieldName, Result projectee) {
    var whnf = projectee.type.normalize(NormalizeMode.WHNF);
    if (!(whnf instanceof CallTerm.Struct structCall))
      return wantButNo(struct, whnf, "struct type");

    var structCore = structCall.ref().core;
    if (structCore == null) throw new UnsupportedOperationException("TODO");
    var projected = structCore.fields().find(field -> Objects.equals(field.ref().name(), fieldName));
    if (projected.isEmpty()) {
      // TODO[ice]: field not found
      throw new TyckerException();
    }
    // TODO[ice]: instantiate the type
    var field = projected.get();
    var fieldRef = field.ref();
    var ctxTele = Def.defContextTele(fieldRef);
    var structSubst = Unfolder.buildSubst(structCore.telescope(), structCall.args());
    var levels = levelStuffs(struct.sourcePos(), fieldRef);
    var tele = Term.Param.subst(fieldRef.core.fieldTele(), structSubst, levels._1);
    var access = new CallTerm.Access(projectee.wellTyped, fieldRef,
      ctxTele.map(Term.Param::toArg), levels._2,
      structCall.args(), tele.map(Term.Param::toArg));
    return new Result(IntroTerm.Lambda.make(tele, access),
      FormTerm.Pi.make(false, tele, field.result().subst(structSubst, levels._1)));
  }

  private Result visitProj(Expr tuple, int ix, Result projectee) {
    var whnf = projectee.type.normalize(NormalizeMode.WHNF);
    if (!(whnf instanceof FormTerm.Sigma sigma && !sigma.co()))
      return wantButNo(tuple, whnf, "sigma type");
    var telescope = sigma.params();
    var index = ix - 1;
    if (index < 0) {
      // TODO[ice]: too small index
      throw new TyckerException();
    } else if (index > telescope.size()) {
      // TODO[ice]: too large index
      throw new TyckerException();
    }
    var type = telescope.get(index).type();
    // instantiate the type
    var subst = new Substituter.TermSubst(MutableMap.of());
    telescope.view().take(index).reversed().forEachIndexed((i, param) ->
      subst.add(param.ref(), new ElimTerm.Proj(projectee.wellTyped, i + 1)));
    return new Result(new ElimTerm.Proj(projectee.wellTyped, ix), type.subst(subst));
  }

  @Override public Result visitHole(Expr.@NotNull HoleExpr expr, Term term) {
    // TODO[ice]: deal with unit type
    var name = Constants.ANONYMOUS_PREFIX;
    if (term == null) term = localCtx.freshHole(FormTerm.Univ.OMEGA, name, expr.sourcePos())._2;
    var freshHole = localCtx.freshHole(term, name, expr.sourcePos());
    if (expr.explicit()) reporter.report(new Goal(expr, freshHole._1));
    return new Result(freshHole._2, term);
  }

  @Rule.Synth @Override public Result visitApp(Expr.@NotNull AppExpr expr, @Nullable Term term) {
    var f = expr.function().accept(this, null);
    var resultTerm = f.wellTyped;
    if (!(f.type.normalize(NormalizeMode.WHNF) instanceof FormTerm.Pi piTerm))
      return wantButNo(expr, f.type, "pi type");
    var pi = piTerm;
    var subst = new Substituter.TermSubst(MutableMap.of());
    for (var iter = expr.arguments().iterator(); iter.hasNext(); ) {
      var arg = iter.next();
      var argLicit = arg.explicit();
      var namedArg = arg.term();
      while (pi.param().explicit() != argLicit ||
        namedArg.name() != null && !Objects.equals(pi.param().ref().name(), namedArg.name())) {
        if (argLicit || namedArg.name() != null) {
          // that implies paramLicit == false
          var genName = pi.param().ref().name().concat(Constants.GENERATED_POSTFIX);
          var holeApp = localCtx.freshHole(pi.param().type(), genName, namedArg.expr().sourcePos())._2;
          // TODO: maybe we should create a concrete hole and check it against the type
          //  in case we can synthesize this term via its type only
          var holeArg = new Arg<>(holeApp, false);
          resultTerm = CallTerm.make(resultTerm, holeArg);
          pi = instPi(expr, pi, subst, holeArg);
        } else {
          // TODO[ice]: no implicit argument expected, but inserted.
          throw new TyckerException();
        }
      }
      var elabArg = namedArg.expr().accept(this, pi.param().type());
      var newArg = new Arg<>(elabArg.wellTyped, argLicit);
      resultTerm = CallTerm.make(resultTerm, newArg);
      // so, in the end, the pi term is not updated, its body would be the eliminated type
      if (iter.hasNext()) pi = instPi(expr, pi, subst, newArg);
      else subst.map().put(pi.param().ref(), newArg.term());
    }
    var codomain = pi.body().subst(subst);
    if (term != null) unifyTyThrowing(term, codomain, expr);
    return new Result(resultTerm, codomain);
  }

  private FormTerm.Pi instPi(@NotNull Expr expr, @NotNull FormTerm.Pi pi, Substituter.TermSubst subst, Arg<Term> newArg) {
    subst.add(pi.param().ref(), newArg.term());
    return pi.body().subst(subst).normalize(NormalizeMode.WHNF) instanceof FormTerm.Pi newPi
      ? newPi : wantButNo(expr, pi.body(), "pi type");
  }

  @Rule.Check(partialSynth = true)
  @Override public Result visitTup(Expr.@NotNull TupExpr expr, @Nullable Term term) {
    var items = Buffer.<Term>of();
    final var resultLast = new Ref<Term>();
    final var resultTele = Buffer.<Term.@NotNull Param>of();
    if (term == null) {
      // TODO[ice]: forbid one-variable tuple maybe?
      expr.items()
        .map(item -> item.accept(this, null))
        .forEach(result -> {
          items.append(result.wellTyped);
          if (resultLast.value != null) resultTele.append(
            new Term.Param(new LocalVar(Constants.ANONYMOUS_PREFIX), resultLast.value, true));
          resultLast.value = result.type;
        });
    } else if (!(term instanceof FormTerm.Sigma dt && !dt.co())) {
      return wantButNo(expr, term, "sigma type");
    } else {
      var againstTele = dt.params().view();
      var last = dt.params().last().type();
      for (var iter = expr.items().iterator(); iter.hasNext(); ) {
        var item = iter.next();
        if (againstTele.isEmpty()) {
          if (iter.hasNext()) {
            // TODO[ice]: not enough sigma elements
            throw new TyckerException();
          } else {
            var result = item.accept(this, last);
            items.append(result.wellTyped);
            resultLast.value = result.type;
          }
        } else {
          var result = item.accept(this, againstTele.first().type());
          items.append(result.wellTyped);
          var ref = againstTele.first().ref();
          resultTele.append(new Term.Param(ref, result.type, againstTele.first().explicit()));
          againstTele = againstTele.drop(1);
          if (againstTele.isNotEmpty()) {
            final var subst = new Substituter.TermSubst(ref, result.wellTyped);
            againstTele = Term.Param.subst(againstTele, subst).view();
            last = last.subst(subst);
          }
        }
      }
    }
    resultTele.append(new Term.Param(new LocalVar(Constants.ANONYMOUS_PREFIX), resultLast.value, true));
    var resultType = new FormTerm.Sigma(false, resultTele.toImmutableSeq());
    return new Result(new IntroTerm.Tuple(items.toImmutableSeq()), resultType);
  }

  @Override public Result catchUnhandled(@NotNull Expr expr, Term term) {
    throw new UnsupportedOperationException(expr.toDoc().renderWithPageWidth(80)); // TODO[kiva]: get terminal width
  }

  public static final class TyckInterruptedException extends InterruptException {
    @Override public InterruptStage stage() {
      return InterruptStage.Tycking;
    }
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
  public static record Result(@NotNull Term wellTyped, @NotNull Term type) {
    @Contract(value = " -> new", pure = true) public @NotNull Tuple2<Term, Term> toTuple() {
      return Tuple.of(type, wellTyped);
    }
  }
}

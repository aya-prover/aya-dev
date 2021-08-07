// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Tuple3;
import kala.tuple.Unit;
import kala.value.Ref;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LevelGenVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.api.util.InternalException;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Signatured;
import org.aya.concrete.visitor.ExprRefSubst;
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
import org.aya.tyck.unify.EqnSet;
import org.aya.tyck.unify.TypedDefEq;
import org.aya.tyck.unify.level.LevelEqnSet;
import org.aya.util.Constants;
import org.aya.util.Ordering;
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
  public final @NotNull LevelEqnSet levelEqns = new LevelEqnSet();
  public final @NotNull EqnSet termEqns = new EqnSet();
  public final @NotNull Sort.LvlVar homotopy = new Sort.LvlVar("h", LevelGenVar.Kind.Homotopy, null);
  public final @NotNull Sort.LvlVar universe = new Sort.LvlVar("u", LevelGenVar.Kind.Universe, null);
  public final @NotNull MutableMap<LevelGenVar, Sort.LvlVar> levelMapping = MutableMap.of();

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  @Override public void traceEntrance(@NotNull Expr expr, Term term) {
    tracing(builder -> builder.shift(new Trace.ExprT(expr, term == null ? null : term.freezeHoles())));
  }

  public @NotNull ImmutableSeq<Sort.LvlVar> extractLevels() {
    return Seq.of(homotopy, universe).view()
      .filter(levelEqns::used)
      .appendedAll(levelMapping.valuesView())
      .toImmutableSeq();
  }

  @Override public void traceExit(Result result, @NotNull Expr expr, Term p) {
    tracing(builder -> {
      builder.append(new Trace.TyckT(result.wellTyped.freezeHoles(), result.type.freezeHoles(), expr.sourcePos()));
      builder.reduce();
    });
  }

  public ExprTycker(@NotNull Reporter reporter, @NotNull LocalCtx localCtx, Trace.@Nullable Builder traceBuilder) {
    this.reporter = reporter;
    this.localCtx = localCtx;
    this.traceBuilder = traceBuilder;
  }

  public ExprTycker(@NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder) {
    this(reporter, new LocalCtx(), traceBuilder);
  }

  public @NotNull Result finalize(@NotNull Result result) {
    solveMetas();
    return new Result(result.wellTyped.zonk(this), result.type.zonk(this));
  }

  public void solveMetas() {
    //noinspection StatementWithEmptyBody
    while (termEqns.simplify(reporter, levelEqns, traceBuilder)) ;
    levelEqns.solve();
  }

  public @NotNull Result checkNoZonk(@NotNull Expr expr, @Nullable Term type) {
    return expr.accept(this, type);
  }

  public @NotNull Result checkExpr(@NotNull Expr expr, @Nullable Term type) {
    return finalize(checkNoZonk(expr, type));
  }

  @Rule.Check(partialSynth = true)
  @Override public Result visitLam(Expr.@NotNull LamExpr expr, @Nullable Term term) {
    if (term == null) term = generatePi(expr);
    if (term instanceof CallTerm.Hole) unifyTy(term, generatePi(expr), expr.sourcePos());
    if (!(term.normalize(NormalizeMode.WHNF) instanceof FormTerm.Pi dt)) {
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
      var body = dt.substBody(resultParam.toTerm());
      var rec = expr.body().accept(this, body);
      return new Result(new IntroTerm.Lambda(resultParam, rec.wellTyped), dt);
    });
  }

  private @NotNull Term generatePi(Expr.@NotNull LamExpr expr) {
    var sourcePos = expr.param().sourcePos();
    var domain = localCtx.freshHole(FormTerm.Univ.OMEGA, sourcePos)._2;
    var codomain = localCtx.freshHole(FormTerm.Univ.OMEGA, sourcePos)._2;
    return new FormTerm.Pi(Term.Param.mock(domain, expr.param().explicit()), codomain);
  }

  private @NotNull Result wantButNo(@NotNull Expr expr, @NotNull Term term, String expectedText) {
    reporter.report(new BadTypeError(expr, Doc.plain(expectedText), term));
    return new Result(new ErrorTerm(expr.toDoc()), term);
  }

  private @NotNull Sort.CoreLevel transformLevel(@NotNull Level<LevelGenVar> level, Sort.LvlVar polymorphic) {
    if (level instanceof Level.Polymorphic) return levelEqns.markUsed(polymorphic);
    if (level instanceof Level.Maximum m)
      return Sort.CoreLevel.merge(m.among().map(l -> transformLevel(l, polymorphic)));
    Level<Sort.LvlVar> core;
    if (level instanceof Level.Reference<LevelGenVar> v)
      core = new Level.Reference<>(levelMapping.getOrPut(v.ref(), () -> new Sort.LvlVar(v.ref().name(), v.ref().kind(), null)), v.lift());
    else if (level instanceof Level.Infinity<LevelGenVar>) core = new Level.Infinity<>();
    else if (level instanceof Level.Constant<LevelGenVar> c) core = new Level.Constant<>(c.value());
    else throw new IllegalArgumentException(level.toString());
    return new Sort.CoreLevel(core);
  }

  @Rule.Synth @Override public Result visitUniv(Expr.@NotNull UnivExpr expr, @Nullable Term term) {
    var u = transformLevel(expr.uLevel(), universe);
    var h = transformLevel(expr.hLevel(), homotopy);
    var sort = new Sort(u, h);
    if (term == null) return new Result(new FormTerm.Univ(sort), new FormTerm.Univ(sort.succ(1)));
    var normTerm = term.normalize(NormalizeMode.WHNF);
    if (normTerm instanceof FormTerm.Univ univ) {
      levelEqns.add(sort.succ(1), univ.sort(), Ordering.Lt, expr.sourcePos());
      return new Result(new FormTerm.Univ(sort), univ);
    } else {
      var succ = new FormTerm.Univ(sort.succ(1));
      unifyTyReported(normTerm, succ, expr);
      return new Result(new FormTerm.Univ(sort), succ);
    }
  }

  @Rule.Synth @Override public Result visitRef(Expr.@NotNull RefExpr expr, @Nullable Term term) {
    var result = doVisitRef(expr, term);
    expr.theCore().set(result.wellTyped);
    return result;
  }

  @NotNull private Result doVisitRef(Expr.@NotNull RefExpr expr, @Nullable Term term) {
    var var = expr.resolvedVar();
    if (var instanceof LocalVar loc) {
      var ty = localCtx.get(loc);
      return unifyTyMaybeInsert(term, ty, new RefTerm(loc, ty), expr);
    } else if (var instanceof DefVar<?, ?> defVar) {
      var result = inferRef(expr.sourcePos(), defVar, term);
      return unifyTyMaybeInsert(term, result.type, result.wellTyped, expr);
    } else throw new IllegalStateException("Unknown var: " + var.getClass());
  }

  @SuppressWarnings("unchecked")
  public @NotNull Result inferRef(@NotNull SourcePos pos, @NotNull DefVar<?, ?> var, Term expected) {
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
      var telescopes = CtorDef.telescopes(conVar, level._2);
      var tele = Term.Param.subst(Def.defTele(conVar), level._1);
      var type = FormTerm.Pi.make(tele, Def.defResult(conVar).subst(Substituter.TermSubst.EMPTY, level._1));
      var body = telescopes.toConCall(conVar);
      return new Result(IntroTerm.Lambda.make(tele, body), type);
    } else if (var.core instanceof FieldDef || var.concrete instanceof Decl.StructField) {
      // the code runs to here because we are tycking a StructField in a StructDecl
      // there should be two-stage check for this case:
      //  - check the definition's correctness: happens here
      //  - check the field value's correctness: happens in `visitNew` after the body was instantiated
      var field = (DefVar<FieldDef, Decl.StructField>) var;
      var ty = Def.defResult(field);
      var fieldPos = field.concrete.sourcePos();
      var refExpr = new Expr.RefExpr(fieldPos, field, field.concrete.ref.name());
      // TODO[ice]: correct this RefTerm
      return unifyTyMaybeInsert(expected, ty, new RefTerm(new LocalVar(field.name(), fieldPos), ty), refExpr);
    } else {
      final var msg = "Def var `" + var.name() + "` has core `" + var.core + "` which we don't know.";
      throw new IllegalStateException(msg);
    }
  }

  private @NotNull <D extends Def, S extends Signatured> ExprTycker.Result
  defCall(@NotNull SourcePos pos, DefVar<D, S> defVar, CallTerm.Factory<D, S> function) {
    var level = levelStuffs(pos, defVar);
    var tele = Term.Param.subst(Def.defTele(defVar), level._1);
    // unbound these abstracted variables
    // ice: should we rename the vars in this telescope? Probably not.
    var body = function.make(defVar,
      level._2,
      tele.map(Term.Param::toArg));
    var type = FormTerm.Pi.make(tele, Def.defResult(defVar).subst(Substituter.TermSubst.EMPTY, level._1));
    return new Result(IntroTerm.Lambda.make(tele, body), type);
  }

  private @NotNull Tuple2<LevelSubst.Simple, ImmutableSeq<Sort.CoreLevel>>
  levelStuffs(@NotNull SourcePos pos, DefVar<? extends Def, ? extends Signatured> defVar) {
    var levelSubst = new LevelSubst.Simple(MutableMap.of());
    var levelVars = Def.defLevels(defVar).map(v -> {
      var lvlVar = new Sort.LvlVar(defVar.name() + "." + v.name(), v.kind(), pos);
      levelSubst.solution().put(v, new Sort.CoreLevel(new Level.Reference<>(lvlVar)));
      return lvlVar;
    });
    levelEqns.vars().appendAll(levelVars);
    return Tuple.of(levelSubst, levelVars.map(Level.Reference::new).map(Sort.CoreLevel::new));
  }

  private boolean unifyTy(@NotNull Term upper, @NotNull Term lower, @NotNull SourcePos pos) {
    tracing(builder -> builder.append(new Trace.UnifyT(lower, upper, pos)));
    return unifier(pos, Ordering.Lt).compare(lower, upper, FormTerm.Univ.OMEGA);
  }

  public @NotNull TypedDefEq unifier(@NotNull SourcePos pos, @NotNull Ordering ord) {
    return new TypedDefEq(ord, reporter, levelEqns, termEqns, traceBuilder, pos);
  }

  /**
   * Check if <code>lower</code> is a subtype of <code>upper</code>,
   * and report a type error if it's not the case.
   *
   * @see ExprTycker#unifyTyMaybeInsert(Term, Term, Term, Expr)
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
  private Result unifyTyMaybeInsert(@Nullable Term upper, @NotNull Term lower, @NotNull Term term, Expr loc) {
    if (upper == null) return new Result(term, lower);
    if (unifyTy(upper, lower, loc.sourcePos())) return new Result(term, lower);
    var subst = new Substituter.TermSubst(MutableMap.of());
    while (lower.normalize(NormalizeMode.WHNF) instanceof FormTerm.Pi pi && !pi.param().explicit()) {
      var mock = mockTerm(pi.param(), loc.sourcePos());
      term = CallTerm.make(term, Arg.implicit(mock));
      subst.add(pi.param().ref(), mock);
      lower = pi.body().subst(subst);
      if (unifyTy(upper, lower, loc.sourcePos())) return new Result(term, lower);
    }
    reporter.report(new UnifyError(loc, upper, lower));
    return new Result(new ErrorTerm(term.toDoc()), upper);
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
      return new Result(new FormTerm.Pi(resultParam, last.wellTyped), against);
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
    return new Result(new FormTerm.Sigma(Term.Param.fromBuffer(resultTele)), against);
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

    var fields = Buffer.<Tuple2<DefVar<FieldDef, Decl.StructField>, Term>>of();
    var missing = Buffer.<Var>of();
    var conFields = expr.fields();

    for (var defField : structRef.core.fields) {
      var conFieldOpt = conFields.find(t -> t.name().equals(defField.ref().name()));
      if (conFieldOpt.isEmpty()) {
        if (defField.body.isEmpty())
          missing.append(defField.ref()); // no value available, skip and prepare error reporting
        else {
          // use default value from defField
          var field = defField.body.get().subst(subst);
          fields.append(Tuple.of(defField.ref(), field));
          subst.add(defField.ref(), field);
        }
        continue;
      }
      var conField = conFieldOpt.get();
      conFields = conFields.dropWhile(t -> t == conField);
      var type = Def.defResult(defField.ref()).subst(subst);
      var fieldSubst = new ExprRefSubst(reporter);
      var telescope = defField.ref().core.selfTele;
      var bindings = conField.bindings();
      if (telescope.sizeLessThan(bindings.size())) {
        // TODO[ice]: number of args don't match
        throw new TyckerException();
      }
      var teleView = telescope.view();
      for (int i = 0; i < bindings.size(); i++) {
        fieldSubst.good().put(bindings.get(i).data(), teleView.first().ref());
        teleView = teleView.drop(1);
      }
      final var teleViewFinal = teleView;
      var field = localCtx.with(telescope, () -> conField.body()
        .accept(fieldSubst, Unit.unit())
        .accept(this, FormTerm.Pi.make(teleViewFinal, type)).wellTyped);
      fields.append(Tuple.of(defField.ref(), field));
      subst.add(defField.ref(), field);
    }

    if (missing.isNotEmpty()) {
      reporter.report(new FieldProblem.MissingFieldError(expr.sourcePos(), missing.toImmutableSeq()));
      return new Result(new ErrorTerm(expr.toDoc()), structCall);
    }
    if (conFields.isNotEmpty()) {
      reporter.report(new FieldProblem.NoSuchFieldError(expr.sourcePos(), conFields.map(Expr.Field::name).toImmutableSeq()));
      return new Result(new ErrorTerm(expr.toDoc()), structCall);
    }

    if (term != null) unifyTyReported(term, structCall, expr);
    return new Result(new IntroTerm.New(structCall, ImmutableMap.from(fields)), structCall);
  }

  @Rule.Synth @Override public Result visitProj(Expr.@NotNull ProjExpr expr, @Nullable Term term) {
    var result = doVisitProj(expr, term);
    expr.theCore().set(result.wellTyped);
    return result;
  }

  @NotNull private Result doVisitProj(Expr.@NotNull ProjExpr expr, @Nullable Term term) {
    var from = expr.tup();
    var result = expr.ix().fold(
      ix -> visitProj(from, ix, expr),
      sp -> visitAccess(from, sp.data(), expr)
    );
    return unifyTyMaybeInsert(term, result.type, result.wellTyped, expr);
  }

  private Result visitAccess(Expr struct, String fieldName, Expr.@NotNull ProjExpr expr) {
    var projectee = struct.accept(this, null);
    var whnf = projectee.type.normalize(NormalizeMode.WHNF);
    if (!(whnf instanceof CallTerm.Struct structCall))
      return wantButNo(struct, whnf, "struct type");

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
    expr.resolvedIx().value = fieldRef;

    var structSubst = Unfolder.buildSubst(structCore.telescope(), structCall.args());
    var levels = levelStuffs(struct.sourcePos(), fieldRef);
    var tele = Term.Param.subst(fieldRef.core.selfTele, structSubst, levels._1);
    var access = new CallTerm.Access(projectee.wellTyped, fieldRef, levels._2,
      structCall.args(), tele.map(Term.Param::toArg));
    return new Result(IntroTerm.Lambda.make(tele, access),
      FormTerm.Pi.make(tele, field.result().subst(structSubst, levels._1)));
  }

  private @NotNull Result visitProj(@NotNull Expr tuple, int ix, Expr.@NotNull ProjExpr proj) {
    var projectee = tuple.accept(this, null);
    var whnf = projectee.type.normalize(NormalizeMode.WHNF);
    if (!(whnf instanceof FormTerm.Sigma sigma))
      return wantButNo(tuple, whnf, "sigma type");
    var telescope = sigma.params();
    var index = ix - 1;
    if (index < 0 || index >= telescope.size()) {
      reporter.report(new ProjIxError(proj, ix, telescope.size()));
      var projDoc = proj.toDoc();
      return new Result(new ErrorTerm(projDoc), ErrorTerm.typeOf(projDoc));
    }
    var type = telescope.get(index).type();
    var subst = ElimTerm.Proj.projSubst(projectee.wellTyped, index, telescope);
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
    var app = f.wellTyped;
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
          var holeApp = mockTerm(pi.param(), namedArg.expr().sourcePos());
          app = CallTerm.make(app, Arg.implicit(holeApp));
          pi = instPi(pi, subst, holeApp);
          if (pi == null) return new Result(new ErrorTerm(expr.toDoc()), f.type);
        } else {
          // TODO[ice]: no implicit argument expected, but inserted.
          throw new TyckerException();
        }
      }
      var elabArg = namedArg.expr().accept(this, pi.param().type()).wellTyped;
      app = CallTerm.make(app, new Arg<>(elabArg, argLicit));
      // so, in the end, the pi term is not updated, its body would be the eliminated type
      if (iter.hasNext()) pi = instPi(pi, subst, elabArg);
      if (pi == null) return new Result(new ErrorTerm(expr.toDoc()), f.type);
      else subst.map().put(pi.param().ref(), elabArg);
    }
    return unifyTyMaybeInsert(term, pi.body().subst(subst), app, expr);
  }

  private @NotNull Term mockTerm(Term.Param param, SourcePos pos) {
    // TODO: maybe we should create a concrete hole and check it against the type
    //  in case we can synthesize this term via its type only
    var genName = param.ref().name().concat(Constants.GENERATED_POSTFIX);
    return localCtx.freshHole(param.type(), genName, pos)._2;
  }

  private FormTerm.@Nullable Pi instPi(@NotNull FormTerm.Pi pi, Substituter.TermSubst subst, @NotNull Term arg) {
    subst.add(pi.param().ref(), arg);
    return pi.body().subst(subst).normalize(NormalizeMode.WHNF) instanceof FormTerm.Pi newPi ? newPi : null;
  }

  @Rule.Check(partialSynth = true)
  @Override public Result visitTup(Expr.@NotNull TupExpr expr, @Nullable Term term) {
    var items = Buffer.<Term>of();
    final var resultLast = new Ref<Term>();
    final var resultTele = Buffer.<Term.@NotNull Param>of();
    if (term == null || term instanceof CallTerm.Hole) {
      // `expr.items()` is larger than 1 due to parser
      expr.items()
        .map(item -> item.accept(this, null))
        .forEach(result -> {
          items.append(result.wellTyped);
          if (resultLast.value != null) resultTele.append(
            new Term.Param(Constants.anonymous(), resultLast.value, true));
          resultLast.value = result.type;
        });
    } else if (!(term instanceof FormTerm.Sigma dt)) {
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
    resultTele.append(new Term.Param(Constants.anonymous(), resultLast.value, true));
    var resultType = new FormTerm.Sigma(resultTele.toImmutableSeq());
    if (term instanceof CallTerm.Hole) unifyTy(term, resultType, expr.sourcePos());
    return new Result(new IntroTerm.Tuple(items.toImmutableSeq()), resultType);
  }

  @Override public Result catchUnhandled(@NotNull Expr expr, Term term) {
    return new Result(ErrorTerm.unexpected(expr.toDoc()), term);
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

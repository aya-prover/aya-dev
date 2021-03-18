// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.error.Reporter;
import org.aya.api.ref.DefVar;
import org.aya.api.util.BreakingException;
import org.aya.api.util.InterruptException;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Decl;
import org.aya.concrete.Expr;
import org.aya.concrete.Signatured;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.def.StructDef;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.generic.Arg;
import org.aya.pretty.doc.Doc;
import org.aya.ref.LocalVar;
import org.aya.tyck.error.BadTypeError;
import org.aya.tyck.error.MissingFieldError;
import org.aya.tyck.error.NoSuchFieldError;
import org.aya.tyck.error.UnifyError;
import org.aya.tyck.sort.Sort;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.PatDefEq;
import org.aya.tyck.unify.Rule;
import org.aya.tyck.unify.TypedDefEq;
import org.aya.util.Constants;
import org.aya.util.Ordering;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.function.TriFunction;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.tuple.Tuple3;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

public class ExprTycker implements Expr.BaseVisitor<Term, ExprTycker.Result> {
  public final @NotNull MetaContext metaContext;
  public final @NotNull LocalCtx localCtx;
  public Trace.@Nullable Builder traceBuilder = null;

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  @Override public void traceEntrance(@NotNull Expr expr, Term term) {
    tracing(builder -> builder.shift(new Trace.ExprT(expr, term)));
  }

  @Override public void traceExit(Result result, @NotNull Expr expr, Term p) {
    tracing(builder -> {
      builder.shift(new Trace.TyckT(result.wellTyped, result.type, expr.sourcePos()));
      builder.reduce();
      builder.reduce();
    });
  }

  public ExprTycker(@NotNull Reporter reporter) {
    this(new MetaContext(reporter));
  }

  public ExprTycker(@NotNull MetaContext metaContext) {
    this(metaContext, new LocalCtx());
  }

  public ExprTycker(@NotNull MetaContext metaContext, @NotNull LocalCtx localCtx) {
    this.localCtx = localCtx;
    this.metaContext = metaContext;
  }

  public @NotNull Result finalize(@NotNull Result result) {
    return new Result(
      result.wellTyped.strip(metaContext),
      result.type.strip(metaContext)
    );
  }

  public @NotNull Result checkExpr(@NotNull Expr expr, @Nullable Term type) throws TyckInterruptedException {
    return finalize(expr.accept(this, type));
  }

  @Rule.Check(partialSynth = true)
  @Override public Result visitLam(Expr.@NotNull LamExpr expr, @Nullable Term term) {
    if (term == null) {
      var domain = new LocalVar(Constants.ANONYMOUS_PREFIX);
      var codomain = new LocalVar(Constants.ANONYMOUS_PREFIX);
      term = new PiTerm(false, Term.Param.mock(domain, expr.param().explicit()), new CallTerm.Hole(codomain));
    }
    if (!(term.normalize(NormalizeMode.WHNF) instanceof PiTerm dt && !dt.co())) {
      return wantButNo(expr, term, "pi type");
    }
    var param = expr.param();
    var var = param.ref();
    var lamParam = param.type();
    var type = dt.param().type();
    if (lamParam != null) {
      var result = lamParam.accept(this, UnivTerm.OMEGA);
      var comparison = unifyTy(result.wellTyped, type, lamParam);
      if (!comparison) {
        // TODO[ice]: expected type mismatch lambda type annotation
        throw new TyckerException();
      } else type = result.wellTyped;
    }
    var resultParam = new Term.Param(var, type, param.explicit());
    return localCtx.with(var, type, () -> {
      var body = dt.body().subst(dt.param().ref(), new RefTerm(var));
      var rec = expr.body().accept(this, body);
      return new Result(new LamTerm(resultParam, rec.wellTyped), dt);
    });
  }

  <T> T wantButNo(@NotNull Expr expr, Term term, String expectedText) {
    metaContext.report(new BadTypeError(expr, Doc.plain(expectedText), term));
    throw new TyckInterruptedException();
  }

  @Rule.Synth
  @Override public Result visitUniv(Expr.@NotNull UnivExpr expr, @Nullable Term term) {
    if (term == null) return new Result(new UnivTerm(Sort.OMEGA), new UnivTerm(Sort.OMEGA));
    if (term.normalize(NormalizeMode.WHNF) instanceof UnivTerm univ) {
      // TODO[level]
      return new Result(new UnivTerm(Sort.OMEGA), univ);
    }
    return wantButNo(expr, term, "universe term");
  }

  @Rule.Synth @Override public Result visitRef(Expr.@NotNull RefExpr expr, @Nullable Term term) {
    var var = expr.resolvedVar();
    if (var instanceof LocalVar loc) {
      var ty = localCtx.get(loc);
      return result(expr, term, ty, new RefTerm(loc));
    } else if (var instanceof DefVar<?, ?> defVar) {
      var result = inferRef(defVar, term);
      return result(expr, term, result.type, result.wellTyped);
    } else throw new IllegalStateException("TODO: UnivVar not yet implemented");
  }

  @SuppressWarnings("unchecked") public @NotNull Result inferRef(@NotNull DefVar<?, ?> var, Term expected) {
    if (var.core instanceof FnDef || var.concrete instanceof Decl.FnDecl) {
      return defCall((DefVar<FnDef, Decl.FnDecl>) var, CallTerm.Fn::new);
    } else if (var.core instanceof DataDef || var.concrete instanceof Decl.DataDecl) {
      return defCall((DefVar<DataDef, Decl.DataDecl>) var, CallTerm.Data::new);
    } else if (var.core instanceof StructDef || var.concrete instanceof Decl.StructDecl) {
      return defCall((DefVar<StructDef, Decl.StructDecl>) var, CallTerm.Struct::new);
    } else if (var.core instanceof DataDef.Ctor || var.concrete instanceof Decl.DataDecl.DataCtor) {
      var conVar = (DefVar<DataDef.Ctor, Decl.DataDecl.DataCtor>) var;
      var telescopes = DataDef.Ctor.telescopes(conVar);
      var tele = Def.defTele(conVar);
      var type = PiTerm.make(false, tele, Def.defResult(conVar));
      return new Result(LamTerm.make(tele, telescopes.toConCall(conVar)), type);
    } else if (var.core instanceof StructDef.Field || var.concrete instanceof Decl.StructField) {
      // the code runs to here because we are tycking a StructField in a StructDecl
      // there should be two-stage check for this case:
      //  - check the definition's correctness: happens here
      //  - check the field value's correctness: happens in `visitNew` after the body was instantiated
      var field = (DefVar<StructDef.Field, Decl.StructField>) var;
      var ty = Def.defResult(field);
      var refExpr = new Expr.RefExpr(field.concrete.sourcePos(), field);
      // TODO[ice]: correct this RefTerm
      return result(refExpr, expected, ty, new RefTerm(new LocalVar(field.name())));
    } else {
      final var msg = "Def var `" + var.name() + "` has core `" + var.core + "` which we don't know.";
      throw new IllegalStateException(msg);
    }
  }

  private @NotNull Result result(Expr.@NotNull RefExpr expr, @Nullable Term expected, Term ty, Term refTerm) {
    if (expected == null) return new Result(refTerm, ty);
    unifyTyThrowing(expected, ty, expr);
    return new Result(refTerm, ty);
  }

  private @NotNull <D extends Def, S extends Signatured> ExprTycker.Result
  defCall(DefVar<D, S> defVar, TriFunction<DefVar<D, S>, SeqLike<Arg<Term>>, SeqLike<Arg<Term>>, Term> function) {
    var tele = Def.defTele(defVar);
    // ice: should we rename the vars in this telescope? Probably not.
    var body = function.apply(
      defVar,
      Objects.requireNonNull(defVar.concrete.signature)
        .param().view().map(Term.Param::toArg),
      tele.view().map(Term.Param::toArg)
    );
    var type = PiTerm.make(false, tele, Def.defResult(defVar));
    return new Result(LamTerm.make(tele, body), type);
  }

  private boolean unifyTy(Term upper, Term lower, Expr loc) {
    tracing(builder -> builder.shift(new Trace.UnifyT(lower, upper, loc.sourcePos())));
    tracing(Trace.Builder::reduce);
    var unifier = new TypedDefEq(
      eq -> new PatDefEq(eq, Ordering.Lt, metaContext),
      localCtx,
      loc.sourcePos()
    );
    unifier.traceBuilder = traceBuilder;
    return unifier.compare(lower, upper, UnivTerm.OMEGA);
  }

  private void unifyTyThrowing(Term upper, Term lower, Expr loc) {
    var unification = unifyTy(upper, lower, loc);
    if (!unification) {
      metaContext.report(new UnifyError(loc, upper, lower));
      throw new TyckInterruptedException();
    }
  }

  @Rule.Synth @Override public Result visitPi(Expr.@NotNull PiExpr expr, @Nullable Term term) {
    final var against = term != null ? term : new UnivTerm(Sort.OMEGA);
    var var = expr.param().ref();
    var param = expr.param();
    final var type = param.type();
    if (type == null) {
      // TODO[ice]: report error or generate meta?
      //  I guess probably report error for now.
      throw new TyckerException();
    }
    var result = type.accept(this, against);
    var resultParam = new Term.Param(var, result.wellTyped, param.explicit());
    var last = expr.last().accept(this, against);
    return new Result(new PiTerm(expr.co(), resultParam, last.wellTyped), against);
  }

  @Rule.Synth
  @Override public Result visitTelescopicSigma(Expr.@NotNull TelescopicSigmaExpr expr, @Nullable Term term) {
    final var against = term != null ? term : new UnivTerm(Sort.OMEGA);
    var resultTele = Buffer.<Tuple3<LocalVar, Boolean, Term>>of();
    expr.paramsStream().forEach(tuple -> {
      final var type = tuple._2.type();
      if (type == null) {
        // TODO[ice]: report error or generate meta?
        //  I guess probably report error for now.
        throw new TyckerException();
      }
      var result = type.accept(this, against);
      resultTele.append(Tuple.of(tuple._1, tuple._2.explicit(), result.wellTyped));
    });
    var last = expr.last().accept(this, against);
    return new Result(new SigmaTerm(expr.co(), Term.Param.fromBuffer(resultTele), last.wellTyped), against);
  }

  @Override
  public Result visitNew(Expr.@NotNull NewExpr expr, @Nullable Term term) {
    var struct = expr.struct().accept(this, null).wellTyped;
    if (!(struct instanceof CallTerm.Struct structCall))
      return wantButNo(expr.struct(), struct, "struct type");
    var structRef = structCall.structRef();

    var subst = new Substituter.TermSubst(new MutableHashMap<>());
    var structTele = Def.defTele(structRef);
    structTele.view().zip(structCall.args())
      .forEach(t -> subst.add(t._1.ref(), t._2.term()));

    var fields = Buffer.<Tuple2<String, Term>>of();
    var missing = Buffer.<String>of();
    var conFields = expr.fields().view();

    for (var defField : structRef.core.fields()) {
      var conFieldOpt = conFields.find(t -> t._1.equals(defField.ref().name())).map(t -> t._2);
      if (conFieldOpt.isEmpty()) {
        if (defField.body().isEmpty())
          missing.append(defField.ref().name()); // no value available, skip and prepare error reporting
        else {
          // use default value from defField
          var field = defField.body().get().subst(subst);
          fields.append(Tuple.of(defField.ref().name(), field));
          subst.add(defField.ref(), field);
        }
        continue;
      }
      var conField = conFieldOpt.get();
      conFields = conFields.dropWhile(t -> t._2 == conField);
      var type = Def.defResult(defField.ref()).subst(subst);
      var fieldRes = conField.accept(this, null);
      unifyTyThrowing(type, fieldRes.type.subst(subst), conField);
      var field = fieldRes.wellTyped.subst(subst);
      fields.append(Tuple.of(defField.ref().name(), field));
      subst.add(defField.ref(), field);
    }

    if (!missing.isEmpty()) {
      metaContext.report(new MissingFieldError(expr.sourcePos(), missing.toImmutableSeq()));
      throw new TyckInterruptedException();
    }
    if (!conFields.isEmpty()) {
      metaContext.report(new NoSuchFieldError(expr.sourcePos(), conFields.map(t -> t._1).toImmutableSeq()));
      throw new TyckInterruptedException();
    }

    // TODO: and then create a StructTerm? what about reusing the TupTerm?
    return new Result(new NewTerm(fields.toImmutableSeq()), structCall.subst(subst));
  }

  @Rule.Synth @Override public Result visitProj(Expr.@NotNull ProjExpr expr, @Nullable Term term) {
    var projectee = expr.tup().accept(this, null);
    return expr.ix().fold(
      ix -> visitIntProj(expr, term, projectee),
      sp -> visitStructProj(expr, term, projectee)
    );
  }

  private Result visitStructProj(Expr.@NotNull ProjExpr expr, @Nullable Term term, Result projectee) {
    if (!(projectee.type instanceof CallTerm.Struct structCall))
      return wantButNo(expr.tup(), projectee.type, "struct type");

    throw new UnsupportedOperationException("TODO");
  }

  private Result visitIntProj(Expr.@NotNull ProjExpr expr, @Nullable Term term, Result projectee) {
    if (!(projectee.type instanceof SigmaTerm sigma && !sigma.co()))
      return wantButNo(expr.tup(), projectee.type, "sigma type");
    var telescope = sigma.params();
    var ix = expr.ix().getLeftValue();
    var index = ix - 1;
    if (index < 0) {
      // TODO[ice]: too small index
      throw new TyckerException();
    } else if (index > telescope.size()) {
      // TODO[ice]: too large index
      throw new TyckerException();
    }
    var type = index == telescope.size() ? sigma.body() : telescope.get(index).type();
    // instantiate the type
    var fieldsBefore = telescope.take(index);
    var subst = new Substituter.TermSubst(new MutableHashMap<>());
    fieldsBefore.forEachIndexed((i, param) ->
      subst.add(param.ref(), new ProjTerm(projectee.wellTyped, i + 1)));
    type = type.subst(subst);
    unifyTyThrowing(term, type, expr);
    return new Result(new ProjTerm(projectee.wellTyped, ix), type);
  }

  @Override public Result visitHole(Expr.@NotNull HoleExpr expr, Term term) {
    // TODO[ice]: deal with unit type
    var name = expr.name();
    if (name == null) name = Constants.ANONYMOUS_PREFIX;
    if (term == null) term = new CallTerm.Hole(new LocalVar(name + "_ty"));
    return new Result(new CallTerm.Hole(new LocalVar(name)), term);
  }

  @Rule.Synth @Override public Result visitApp(Expr.@NotNull AppExpr expr, @Nullable Term term) {
    var f = expr.function().accept(this, null);
    var resultTerm = f.wellTyped;
    if (!(f.type instanceof PiTerm piTerm)) return wantButNo(expr, f.type, "pi type");
    var pi = piTerm;
    var subst = new Substituter.TermSubst(new MutableHashMap<>());
    for (var iter = expr.arguments().iterator(); iter.hasNext(); ) {
      var arg = iter.next();
      var param = pi.param().subst(subst);
      var argLicit = arg.explicit();
      Arg<Term> newArg;
      if (param.explicit() == argLicit) {
        var elabArg = arg.term().accept(this, param.type());
        newArg = new Arg<>(elabArg.wellTyped, argLicit);
      } else if (argLicit) {
        // that implies paramLicit == false
        var holeApp = new CallTerm.Hole(new LocalVar(Constants.ANONYMOUS_PREFIX));
        // TODO: maybe we should create a concrete hole and check it against the type
        //  in case we can synthesize this term via its type only
        newArg = new Arg<>(holeApp, false);
      } else {
        // TODO[ice]: no implicit argument expected, but inserted.
        throw new TyckerException();
      }
      resultTerm = CallTerm.make(resultTerm, newArg);
      // so, in the end, the pi term is not updated, its body would be the eliminated type
      if (iter.hasNext()) {
        subst.add(param.ref(), newArg.term());
        if (pi.body() instanceof PiTerm newPi) pi = newPi;
        else wantButNo(expr, pi.body(), "pi type");
      }
    }
    return new Result(resultTerm, pi.body());
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
    } else if (!(term instanceof SigmaTerm dt && !dt.co())) {
      return wantButNo(expr, term, "sigma type");
    } else {
      var againstTele = dt.params().view();
      var last = dt.body();
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
          if (!againstTele.isEmpty()) {
            final var subst = new Substituter.TermSubst(ref, result.wellTyped);
            againstTele = againstTele.map(param -> param.subst(subst));
            last = last.subst(subst);
          }
        }
      }
    }
    var resultType = new SigmaTerm(false, resultTele.toImmutableSeq(), resultLast.value);
    return new Result(new TupTerm(items.toImmutableSeq()), resultType);
  }

  @Override public Result catchUnhandled(@NotNull Expr expr, Term term) {
    throw new UnsupportedOperationException(expr.toDoc().renderWithPageWidth(80)); // TODO[kiva]: get terminal width
  }

  public static final class TyckInterruptedException extends InterruptException {
    @Override public InterruptStage stage() {
      return InterruptStage.Tycking;
    }
  }

  public static class TyckerException extends BreakingException {
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

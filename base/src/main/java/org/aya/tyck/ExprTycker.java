// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.error.Reporter;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.api.util.BreakingException;
import org.aya.api.util.InterruptException;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Decl;
import org.aya.concrete.Expr;
import org.aya.concrete.Signatured;
import org.aya.core.def.DataDef;
import org.aya.core.def.FnDef;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.generic.Arg;
import org.aya.pretty.doc.Doc;
import org.aya.ref.LocalVar;
import org.aya.tyck.error.BadTypeError;
import org.aya.tyck.error.UnifyError;
import org.aya.tyck.sort.Sort;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.PatDefEq;
import org.aya.tyck.unify.Rule;
import org.aya.tyck.unify.TypedDefEq;
import org.aya.util.Constants;
import org.aya.util.Ordering;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple3;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class ExprTycker implements Expr.BaseVisitor<Term, ExprTycker.Result> {
  public final @NotNull MetaContext metaContext;
  public final @NotNull MutableMap<Var, Term> localCtx;
  public Trace.@Nullable Builder traceBuilder = null;

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  @Override public void traceEntrance(@NotNull Expr expr, Term term) {
    tracing(builder -> builder.shift(new Trace.ExprT(expr, term)));
  }

  @Override public void traceExit(Result result) {
    tracing(Trace.Builder::reduce);
  }

  public ExprTycker(@NotNull Reporter reporter) {
    this(new MetaContext(reporter));
  }

  public ExprTycker(@NotNull MetaContext metaContext) {
    this(metaContext, new MutableHashMap<>());
  }

  public ExprTycker(@NotNull MetaContext metaContext, @NotNull MutableMap<Var, Term> localCtx) {
    this.localCtx = localCtx;
    this.metaContext = metaContext;
  }

  public @NotNull Result finalize(@NotNull Result result) {
    return new Result(
      result.wellTyped.strip(metaContext),
      result.type.strip(metaContext)
    );
  }

  public @NotNull Result
  checkExpr(@NotNull Expr expr, @Nullable Term type) throws TyckInterruptedException {
    return finalize(expr.accept(this, type));
  }

  @Rule.Check(partialSynth = true)
  @Override
  public Result visitLam(Expr.@NotNull LamExpr expr, @Nullable Term term) {
    if (term == null) {
      var domain = new LocalVar(Constants.ANONYMOUS_PREFIX);
      var codomain = new LocalVar(Constants.ANONYMOUS_PREFIX);
      term = new PiTerm(false, Term.Param.mock(domain, expr.param().explicit()), new AppTerm.HoleApp(codomain));
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
      var comparison = new TypedDefEq(
        eq -> new PatDefEq(eq, Ordering.Lt, metaContext),
        localCtx
      ).compare(result.wellTyped, type, UnivTerm.OMEGA);
      if (!comparison) {
        // TODO[ice]: expected type mismatch lambda type annotation
        throw new TyckerException();
      } else type = result.wellTyped;
    }
    var resultParam = new Term.Param(var, type, param.explicit());
    localCtx.put(var, type);
    var body = dt.body().subst(dt.param().ref(), new RefTerm(var));
    var rec = expr.body().accept(this, body);
    return new Result(new LamTerm(resultParam, rec.wellTyped), dt);
  }

  private <T> T wantButNo(@NotNull Expr expr, Term term, String expectedText) {
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

  @SuppressWarnings("unchecked") @Rule.Synth
  @Override public Result visitRef(Expr.@NotNull RefExpr expr, @Nullable Term term) {
    final var var = expr.resolvedVar();
    if (var instanceof DefVar<?, ?> defVar) {
      if (defVar.core instanceof FnDef fn) {
        // TODO[ice]: should we rename the vars in this telescope?
        var tele = fn.telescope();
        var call = new AppTerm.FnCall((DefVar<FnDef, Decl.FnDecl>) defVar, tele.map(Term.Param::toArg));
        return defCall(tele, call, fn.result());
      } else if (defVar.core instanceof DataDef data) {
        var tele = data.telescope();
        var call = new AppTerm.DataCall((DefVar<DataDef, Decl.DataDecl>) defVar, tele.map(Term.Param::toArg));
        return defCall(tele, call, data.result());
      } else if (defVar.concrete instanceof Signatured decl && decl.signature != null)
        return defCall(decl.signature._1, decl.accept(new Signatured.Visitor<ImmutableSeq<Arg<Term>>, @NotNull Term>() {
          @Override public @NotNull Term visitCtor(Decl.@NotNull DataCtor ctor, ImmutableSeq<Arg<Term>> args) {
            throw new UnsupportedOperationException();
          }

          @Override public Term visitDataDecl(Decl.@NotNull DataDecl decl1, ImmutableSeq<Arg<Term>> args) {
            return new AppTerm.DataCall((DefVar<DataDef, Decl.DataDecl>) defVar, args);
          }

          @Override public Term visitFnDecl(Decl.@NotNull FnDecl decl1, ImmutableSeq<Arg<Term>> args) {
            return new AppTerm.FnCall((DefVar<FnDef, Decl.FnDecl>) defVar, args);
          }
        }, decl.signature._1.map(Term.Param::toArg)), decl.signature._2);
      else {
        final var msg = "Def var `" + var.name() + "` has core `" + defVar.core + "` which we don't know.";
        throw new IllegalStateException(msg);
      }
    }
    var ty = localCtx.get(var);
    if (ty == null) throw new IllegalStateException("Unresolved var `" + var.name() + "` tycked.");
    if (term == null) return new Result(new RefTerm(var), ty);
    unify(term, ty, expr);
    return new Result(new RefTerm(var), ty);
  }

  @NotNull private ExprTycker.Result defCall(ImmutableSeq<Term.Param> tele, Term call, Term result) {
    return new Result(LamTerm.make(tele, call), PiTerm.make(false, tele, result));
  }

  private void unify(Term upper, Term lower, Expr errorReportLocation) {
    tracing(builder -> builder.shift(new Trace.UnifyT(lower, upper, errorReportLocation.sourcePos())));
    tracing(Trace.Builder::reduce);
    var unification = new TypedDefEq(
      eq -> new PatDefEq(eq, Ordering.Lt, metaContext),
      localCtx
    ).compare(lower, upper, UnivTerm.OMEGA);
    if (!unification) {
      metaContext.report(new UnifyError(errorReportLocation, upper, lower));
      throw new TyckInterruptedException();
    }
  }

  @Rule.Synth
  @Override public Result visitPi(Expr.@NotNull PiExpr expr, @Nullable Term term) {
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
    var resultTele = Buffer.<Tuple3<Var, Boolean, Term>>of();
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

  @Rule.Synth
  @Override public Result visitProj(Expr.@NotNull ProjExpr expr, @Nullable Term term) {
    var tupleRes = expr.tup().accept(this, null);
    if (!(tupleRes.type instanceof SigmaTerm dt && !dt.co()))
      return wantButNo(expr.tup(), tupleRes.type, "sigma type");
    var telescope = dt.params();
    var index = expr.ix() - 1;
    if (index < 0) {
      // TODO[ice]: too small index
      throw new TyckerException();
    } else if (index > telescope.size()) {
      // TODO[ice]: too large index
      throw new TyckerException();
    }
    var type = index == telescope.size() ? dt.body() : telescope.get(index).type();
    // instantiate the type
    var fieldsBefore = telescope.take(index);
    var subst = new Substituter.TermSubst(new MutableHashMap<>());
    fieldsBefore.forEachIndexed((i, param) ->
      subst.add(param.ref(), new ProjTerm(tupleRes.wellTyped, i + 1)));
    type = type.subst(subst);
    unify(term, type, expr);
    return new Result(new ProjTerm(tupleRes.wellTyped, expr.ix()), type);
  }

  @Override public Result visitHole(Expr.@NotNull HoleExpr expr, Term term) {
    // TODO[ice]: deal with unit type
    var name = expr.name();
    if (name == null) name = Constants.ANONYMOUS_PREFIX;
    if (term == null) term = new AppTerm.HoleApp(new LocalVar(name + "_ty"));
    return new Result(new AppTerm.HoleApp(new LocalVar(name)), term);
  }

  @Rule.Synth
  @Override public Result visitApp(Expr.@NotNull AppExpr expr, @Nullable Term term) {
    var f = expr.function().accept(this, null);
    var resultTerm = f.wellTyped;
    if (!(f.type instanceof PiTerm piTerm)) return wantButNo(expr, f.type, "pi type");
    var pi = piTerm;
    var subst = new Substituter.TermSubst(new MutableHashMap<>());
    for (var iter = expr.arguments().iterator(); iter.hasNext(); ) {
      var arg = iter.next();
      var param = pi.param().subst(subst);
      var paramLicit = param.explicit();
      var argLicit = arg.explicit();
      Arg<Term> newArg;
      if (paramLicit == argLicit) {
        var elabArg = arg.term().accept(this, param.type());
        newArg = new Arg<>(elabArg.wellTyped, argLicit);
      } else if (argLicit) {
        // that implies paramLicit == false
        var holeApp = new AppTerm.HoleApp(new LocalVar(Constants.ANONYMOUS_PREFIX));
        // TODO: maybe we should create a concrete hole and check it against the type
        //  in case we can synthesize this term via its type only
        newArg = new Arg<>(holeApp, false);
      } else {
        // TODO[ice]: no implicit argument expected, but inserted.
        throw new TyckerException();
      }
      resultTerm = AppTerm.make(resultTerm, newArg);
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

  @Override
  public Result catchUnhandled(@NotNull Expr expr, Term term) {
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

  public static record Result(
    @NotNull Term wellTyped,
    @NotNull Term type
  ) {
  }
}

// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.ImmutableTreeSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableTreeSet;
import kala.control.Option;
import org.aya.generic.Constants;
import org.aya.generic.term.DTKind;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.def.DataDefLike;
import org.aya.syntax.core.def.Matchy;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.repr.AyaShape;
import org.aya.syntax.core.repr.ShapeRecognition;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.call.MatchCall;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.core.term.repr.ListTerm;
import org.aya.syntax.core.term.repr.MetaLitTerm;
import org.aya.syntax.core.term.repr.StringTerm;
import org.aya.syntax.core.term.xtt.DimTerm;
import org.aya.syntax.core.term.xtt.DimTyTerm;
import org.aya.syntax.core.term.xtt.EqTerm;
import org.aya.syntax.core.term.xtt.PAppTerm;
import org.aya.syntax.ref.*;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.tyck.ctx.LocalLet;
import org.aya.tyck.error.*;
import org.aya.tyck.pat.ClauseTycker;
import org.aya.tyck.tycker.AbstractTycker;
import org.aya.tyck.tycker.AppTycker;
import org.aya.tyck.tycker.Unifiable;
import org.aya.unify.TermComparator;
import org.aya.unify.Unifier;
import org.aya.util.Ordering;
import org.aya.util.error.Panic;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class ExprTycker extends AbstractTycker implements Unifiable {
  public final @NotNull MutableTreeSet<WithPos<Expr.WithTerm>> withTerms =
    MutableTreeSet.create(Comparator.comparing(SourceNode::sourcePos));
  public final @NotNull MutableList<WithPos<Expr.Hole>> userHoles = MutableList.create();
  private @NotNull LocalLet localLet;
  private final @NotNull ModulePath fileModule;

  public void addWithTerm(@NotNull Expr.WithTerm with, @NotNull SourcePos pos, @NotNull Term type) {
    withTerms.add(new WithPos<>(pos, with));
    with.theCoreType().set(type);
  }

  private ExprTycker(
    @NotNull TyckState state, @NotNull LocalCtx ctx, @NotNull LocalLet let,
    @NotNull Reporter reporter, @NotNull ModulePath fileModule
  ) {
    super(state, ctx, reporter);
    this.localLet = let;
    this.fileModule = fileModule;
  }

  public ExprTycker(@NotNull TyckState state, @NotNull Reporter reporter, @NotNull ModulePath fileModule) {
    this(state, new MapLocalCtx(), new LocalLet(), reporter, fileModule);
  }

  public void solveMetas() {
    state.solveMetas(reporter);
    withTerms.forEach(with -> with.data().theCoreType().update(this::freezeHoles));
    userHoles.forEach(hole -> hole.data().solution().update(this::freezeHoles));
  }

  /**
   * @param type may not be in whnf, because we want unnormalized type to be used for unification.
   */
  public @NotNull Jdg inherit(@NotNull WithPos<Expr> expr, @NotNull Term type) {
    return switch (expr.data()) {
      case Expr.Lambda lam -> {
        var pats = lam.patterns();
        var body = lam.body();

        switch (whnf(type)) {
          case EqTerm eq -> {
            var unlam = lam.unlam();

            if (unlam == null) {
              // first pattern is not bind or meta
              throw new UnsupportedOperationException("TODO");
            }

            var ref = unlam.ref();
            body = unlam.body();

            Closure.Locns core;
            try (var _ = subscope(ref, DimTyTerm.INSTANCE)) {
              core = inherit(body, eq.appA(new FreeTerm(ref))).wellTyped().bind(ref);
            }
            checkBoundaries(eq, core, body.sourcePos(), msg ->
              new CubicalError.BoundaryDisagree(expr, msg, new UnifyInfo(state)));
            yield new Jdg.Default(new LamTerm(core), eq);
          }
          case DepTypeTerm(var kind, var dom, var cod) when kind == DTKind.Pi -> {
            // unifyTyReported(param, dom, expr);
            try (var _ = subscope(ref, dom)) {
              var core = inherit(body, cod.apply(new FreeTerm(ref))).wellTyped().bind(ref);
              yield new Jdg.Default(new LamTerm(core), type);
            }
          }
          case MetaCall metaCall -> {
            var pi = metaCall.asDt(this::whnf, "_dom", "_cod", DTKind.Pi);
            if (pi == null) yield fail(expr.data(), type, BadTypeError.absOnNonPi(state, expr, type));
            unifier(metaCall.ref().pos(), Ordering.Eq).compare(metaCall, pi, null);
            try (var _ = subscope(ref, pi.param())) {
              var core = inherit(body, pi.body().apply(new FreeTerm(ref))).wellTyped().bind(ref);
              yield new Jdg.Default(new LamTerm(core), pi);
            }
          }
          default -> fail(expr.data(), type, BadTypeError.absOnNonPi(state, expr, type));
        }
      }
      case Expr.Hole hole -> {
        var freshHole = freshMeta(Constants.randomName(hole), expr.sourcePos(),
          new MetaVar.OfType(type), hole.explicit());
        hole.solution().set(freshHole);
        Jdg filling = null;
        if (hole.filling() != null) filling = synthesize(hole.filling());
        userHoles.append(new WithPos<>(expr.sourcePos(), hole));
        if (hole.explicit()) fail(new Goal(state, freshHole, filling, localCtx().clone(), hole.accessibleLocal()));
        yield new Jdg.Default(freshHole, type);
      }
      case Expr.LitInt(var end) -> {
        var ty = whnf(type);
        if (ty == DimTyTerm.INSTANCE) {
          if (end == 0 || end == 1) yield new Jdg.Default(end == 0 ? DimTerm.I0 : DimTerm.I1, ty);
          else yield fail(expr.data(), new PrimError.BadInterval(expr.sourcePos(), end));
        }
        yield inheritFallbackUnify(ty, synthesize(expr), expr);
      }
      case Expr.BinTuple(var lhs, var rhs) -> switch (whnf(type)) {
        case DepTypeTerm(var kind, var lhsT, var rhsTClos) when kind == DTKind.Sigma -> {
          var lhsX = inherit(lhs, lhsT).wellTyped();
          var rhsX = inherit(rhs, rhsTClos.apply(lhsX)).wellTyped();
          yield new Jdg.Default(new TupTerm(lhsX, rhsX), type);
        }
        case MetaCall meta -> inheritFallbackUnify(meta, synthesize(expr), expr);
        default -> fail(expr.data(), BadTypeError.sigmaCon(state, expr, type));
      };
      case Expr.Array arr when arr.arrayBlock().isRight()
        && whnf(type) instanceof DataCall dataCall
        && state.shapeFactory.find(dataCall.ref()).getOrNull() instanceof ShapeRecognition recog
        && recog.shape() == AyaShape.LIST_SHAPE -> {
        var arrayBlock = arr.arrayBlock().getRightValue();
        var elementTy = dataCall.args().get(0);
        var results = ImmutableTreeSeq.from(arrayBlock.exprList().map(
          element -> inherit(element, elementTy).wellTyped()));
        yield new Jdg.Default(new ListTerm(results, recog, dataCall), type);
      }
      case Expr.Match(var discriminant, var clauses, var asBindings, var isElim, var returns) -> {
        var wellArgs = discriminant.map(this::synthesize);
        Term storedTy;
        // Type check the type annotation
        if (returns != null) {
          if (asBindings.isEmpty()) {
            unifyTyReported(type, storedTy = ty(returns), returns);
          } else {
            try (var _ = subscope()) {
              asBindings.forEachWith(wellArgs, (as, discr) -> localCtx().put(as, discr.type()));
              storedTy = ty(returns).bindTele(asBindings.view());
            }
            unifyTyReported(type, storedTy.instTele(wellArgs.view().map(Jdg::wellTyped)), returns);
          }
        } else {
          storedTy = type;
        }
        yield new Jdg.Default(match(discriminant, expr.sourcePos(), clauses, wellArgs, storedTy), type);
      }
      case Expr.Let let -> checkLet(let, e -> inherit(e, type));
      default -> inheritFallbackUnify(type, synthesize(expr), expr);
    };
  }

  private @NotNull MatchCall match(
    ImmutableSeq<WithPos<Expr>> discriminant, @NotNull SourcePos exprPos,
    ImmutableSeq<Pattern.Clause> clauses, ImmutableSeq<Jdg> wellArgs, Term type
  ) {
    var telescope = wellArgs.map(x -> new Param(Constants.ANONYMOUS_PREFIX, x.type(), true));
    var clauseTycker = new ClauseTycker.Worker(
      new ClauseTycker(this),
      telescope,
      new DepTypeTerm.Unpi(ImmutableSeq.empty(), type),
      ImmutableSeq.fill(discriminant.size(), i ->
        new LocalVar("match" + i, discriminant.get(i).sourcePos(), GenerateKind.Basic.Tyck)),
      ImmutableSeq.empty(), clauses);
    var wellClauses = clauseTycker.check(exprPos)
      .wellTyped()
      .map(WithPos::data);

    // Find free occurrences
    var usages = new FreeCollector();
    wellClauses.forEach(clause -> usages.apply(clause.body()));
    usages.apply(type);

    // Bind the free occurrences and spawn the lifted clauses as a definition
    var captures = usages.collected();
    var lifted = new Matchy(type.bindTele(wellArgs.size(), captures.view()),
      new QName(QPath.fileLevel(fileModule), "match-" + exprPos.lineColumnString()),
      wellClauses.map(clause -> clause.update(clause.body().bindTele(clause.bindCount(), captures.view()))));

    var wellTerms = wellArgs.map(Jdg::wellTyped);
    return new MatchCall(lifted, wellTerms, captures.map(FreeTerm::new));
  }

  /**
   * @param type   expected type
   * @param result wellTyped + actual type from synthesize
   * @param expr   original expr, used for error reporting
   */
  private @NotNull Jdg inheritFallbackUnify(@NotNull Term type, @NotNull Jdg result, @NotNull WithPos<Expr> expr) {
    type = whnf(type);
    var resultType = result.type();
    // Try coercive subtyping for (Path A ...) into (I -> A)
    if (type instanceof DepTypeTerm(var kind, var dom, var cod) && kind == DTKind.Pi && dom == DimTyTerm.INSTANCE) {
      if (whnf(resultType) instanceof EqTerm eq) {
        if (!isConvertiblePiPath(expr, eq, cod)) return makeErrorResult(type, result);
        var closure = result.wellTyped() instanceof LamTerm(var clos) ? clos
          : new Closure.Jit(i -> new PAppTerm(result.wellTyped(), i, eq.a(), eq.b()));
        return new Jdg.Default(new LamTerm(closure), eq);
      }
    }
    // Try coercive subtyping for (I -> A) into (Path A ...)
    if (type instanceof EqTerm eq) {
      if (whnf(resultType) instanceof DepTypeTerm(
        var kind, var dom, var cod
      ) && kind == DTKind.Pi && dom == DimTyTerm.INSTANCE) {
        if (!isConvertiblePiPath(expr, eq, cod)) return makeErrorResult(type, result);
        var closure = result.wellTyped() instanceof LamTerm(var clos) ? clos
          : new Closure.Jit(i -> new AppTerm(result.wellTyped(), i));
        checkBoundaries(eq, closure, expr.sourcePos(), msg ->
          new CubicalError.BoundaryDisagree(expr, msg, new UnifyInfo(state)));
        return new Jdg.Default(new LamTerm(closure), eq);
      }
    }
    // Try coercive subtyping between classes
    if (type instanceof ClassCall clazz) {
      // Try coercive subtyping for `SomeClass (foo := 114514)` into `SomeClass`
      resultType = whnf(resultType);
      if (resultType instanceof ClassCall resultClazz) {
        // TODO: check whether resultClazz <: clazz
        if (true) {
          // No need to coerce
          if (clazz.args().size() == resultClazz.args().size()) return result;
          var forget = resultClazz.args().drop(clazz.args().size());
          return new Jdg.Default(ClassCastTerm.make(clazz.ref(), result.wellTyped(), clazz.args(), forget), type);
        } else {
          return makeErrorResult(type, result);
        }
      }
    }
    if (unifyTyReported(type, resultType, expr)) return result;
    return makeErrorResult(type, result);
  }

  private static @NotNull Jdg makeErrorResult(@NotNull Term type, @NotNull Jdg result) {
    return new Jdg.Default(new ErrorTerm(result.wellTyped()), type);
  }

  /// @return true if the coercion is successful
  private boolean isConvertiblePiPath(@NotNull WithPos<Expr> expr, EqTerm eq, Closure cod) {
    var ref = new FreeTerm(new LocalVar("i"));
    var wellTyped = false;
    try (var _ = subscope(ref.name(), DimTyTerm.INSTANCE)) {
      wellTyped = unifyTyReported(eq.appA(ref), cod.apply(ref), expr);
    }
    if (!wellTyped) return false;
    if (expr.data() instanceof Expr.WithTerm with)
      addWithTerm(with, expr.sourcePos(), eq);
    return true;
  }

  public @NotNull Term ty(@NotNull WithPos<Expr> expr) {
    return switch (expr.data()) {
      case Expr.Hole hole -> {
        var meta = freshMeta(Constants.randomName(hole), expr.sourcePos(), MetaVar.Misc.IsType, hole.explicit());
        Jdg filling = null;
        if (hole.filling() != null) filling = synthesize(hole.filling());
        if (hole.explicit()) fail(new Goal(state, meta, filling, localCtx().clone(), hole.accessibleLocal()));
        yield meta;
      }
      case Expr.Sort(var kind, var lift) -> new SortTerm(kind, lift);
      case Expr.DepType(var kind, var param, var last) -> {
        var wellParam = ty(param.typeExpr());
        addWithTerm(param, param.sourcePos(), wellParam);
        try (var _ = subscope(param.ref(), wellParam)) {
          yield new DepTypeTerm(kind, wellParam, ty(last).bind(param.ref()));
        }
      }
      case Expr.Let let -> checkLet(let, e -> lazyJdg(ty(e))).wellTyped();
      default -> {
        var result = synthesize(expr);
        if (!(result.type() instanceof SortTerm)) {
          fail(BadTypeError.doNotLike(state, expr, result.type(),
            _ -> Doc.plain("type")));
          yield new ErrorTerm(expr.data());
        }
        yield result.wellTyped();
      }
    };
  }

  public @NotNull Jdg.Sort sort(@NotNull WithPos<Expr> expr) {
    return new Jdg.Sort(sort(expr, ty(expr)));
  }

  private @NotNull SortTerm sort(@NotNull WithPos<Expr> errorMsg, @NotNull Term term) {
    return switch (whnf(term)) {
      case SortTerm u -> u;
      case MetaCall hole -> {
        unifyTyReported(hole, SortTerm.Type0, errorMsg);
        yield SortTerm.Type0;
      }
      default -> {
        fail(BadTypeError.doNotLike(state, errorMsg, term, _ -> Doc.plain("universe")));
        yield SortTerm.Type0;
      }
    };
  }

  public @NotNull Jdg synthesize(@NotNull WithPos<Expr> expr) {
    var result = doSynthesize(expr);
    if (expr.data() instanceof Expr.WithTerm with) {
      addWithTerm(with, expr.sourcePos(), result.type());
    }
    return result;
  }

  public @NotNull Jdg doSynthesize(@NotNull WithPos<Expr> expr) {
    return switch (expr.data()) {
      case Expr.Sugar s -> throw new Panic(s.getClass() + " is desugared, should be unreachable");
      case Expr.App(var f, var a) -> {
        int lift;
        if (f.data() instanceof Expr.Lift(var inner, var level)) {
          lift = level;
          f = inner;
        } else lift = 0;
        if (f.data() instanceof Expr.Ref ref) {
          yield checkApplication(ref, lift, expr.sourcePos(), a);
        } else try {
          yield ArgsComputer.generateApplication(this, a, synthesize(f)).lift(lift);
        } catch (NotPi e) {
          yield fail(expr.data(), BadTypeError.appOnNonPi(state, expr, e.actual));
        }
      }
      case Expr.Proj(var p, var ix, _, _) -> {
        var result = synthesize(p);
        var wellP = result.wellTyped();

        yield ix.fold(iix -> {
          if (iix != ProjTerm.INDEX_FST && iix != ProjTerm.INDEX_SND) {
            return fail(expr.data(), new ClassError.ProjIxError(expr, iix));
          }
          return switch (whnf(result.type())) {
            case MetaCall metaCall -> {
              var sigma = metaCall.asDt(this::whnf, "_fstTy", "_sndTy", DTKind.Sigma);
              if (sigma == null) yield fail(expr.data(), BadTypeError.sigmaAcc(state, expr, iix, result.type()));
              unifier(metaCall.ref().pos(), Ordering.Eq).compare(metaCall, sigma, null);
              if (iix == ProjTerm.INDEX_FST) {
                yield new Jdg.Default(ProjTerm.fst(wellP), sigma.param());
              } else {
                yield new Jdg.Default(ProjTerm.snd(wellP), sigma.body().apply(ProjTerm.fst(wellP)));
              }
            }
            case DepTypeTerm(var kind, var param, var body) when kind == DTKind.Sigma -> {
              var ty = iix == ProjTerm.INDEX_FST ? param : body.apply(ProjTerm.fst(wellP));
              yield new Jdg.Default(ProjTerm.make(wellP, iix == ProjTerm.INDEX_FST), ty);
            }
            default -> fail(expr.data(), BadTypeError.sigmaAcc(state, expr, iix, result.type()));
          };
        }, member -> {
          // TODO: MemberCall
          throw new UnsupportedOperationException("TODO");
        });
      }
      case Expr.Hole hole -> throw new UnsupportedOperationException("TODO");
      case Expr.Lambda lam -> inherit(expr, generatePi(lam, expr.sourcePos()));
      case Expr.LitInt(var integer) -> {
        // TODO[literal]: int literals. Currently the parser does not allow negative literals.
        var defs = state.shapeFactory.findImpl(AyaShape.NAT_SHAPE);
        if (defs.isEmpty()) {
          yield fail(expr.data(), new NoRuleError(expr, null));
        }
        if (defs.sizeGreaterThan(1)) {
          var type = freshMeta(integer + "_ty", expr.sourcePos(), MetaVar.Misc.IsType, false);
          yield new Jdg.Default(new MetaLitTerm(expr.sourcePos(), integer, defs, type), type);
        }
        var match = defs.getFirst();
        var type = new DataCall((DataDefLike) match.def(), 0, ImmutableSeq.empty());
        yield new Jdg.Default(new IntegerTerm(integer, match.recog(), type), type);
      }
      case Expr.Lift(WithPos(var innerPos, Expr.Ref ref), var level) ->
        checkApplication(ref, level, innerPos, ImmutableSeq.empty());
      case Expr.Lift(var inner, var level) -> synthesize(inner).map(x -> x.elevate(level));
      case Expr.LitString litStr -> {
        if (!state.primFactory.have(PrimDef.ID.STRING))
          yield fail(litStr, new NoRuleError(expr, null));
        yield new Jdg.Default(new StringTerm(litStr.string()), state.primFactory.getCall(PrimDef.ID.STRING));
      }
      case Expr.Ref ref -> checkApplication(ref, 0, expr.sourcePos(), ImmutableSeq.empty());
      case Expr.DepType _ -> lazyJdg(ty(expr));
      case Expr.Sort _ -> sort(expr);
      case Expr.BinTuple(var lhs, var rhs) -> {
        var lhsX = synthesize(lhs);
        var rhsX = synthesize(rhs);
        var wellTyped = new TupTerm(lhsX.wellTyped(), rhsX.wellTyped());
        var ty = new DepTypeTerm(DTKind.Sigma, lhsX.type(), Closure.mkConst(rhsX.type()));

        yield new Jdg.Default(wellTyped, ty);
      }
      case Expr.Let let -> checkLet(let, this::synthesize);
      case Expr.Error err -> new Jdg.Default(new ErrorTerm(err), ErrorTerm.typeOf(err));
      case Expr.Array arr when arr.arrayBlock().isRight() -> {
        var arrayBlock = arr.arrayBlock().getRightValue();
        var elements = arrayBlock.exprList();

        // find def
        var defs = state.shapeFactory.findImpl(AyaShape.LIST_SHAPE);
        if (defs.isEmpty()) yield fail(arr, new NoRuleError(expr, null));
        if (defs.sizeGreaterThan(1)) {
          var elMeta = freshMeta("el_ty", expr.sourcePos(), MetaVar.Misc.IsType, false);
          var tyMeta = freshMeta("arr_ty", expr.sourcePos(), MetaVar.Misc.IsType, false);
          var results = elements.map(element -> inherit(element, elMeta).wellTyped());
          yield new Jdg.Default(new MetaLitTerm(expr.sourcePos(), results, defs, tyMeta), tyMeta);
        }
        var match = defs.getFirst();
        var def = (DataDefLike) match.def();

        // List (A : Type)
        var sort = def.signature().telescopeRich(0);
        // the sort of type below.
        var elementTy = freshMeta(sort.name(), expr.sourcePos(), new MetaVar.OfType(sort.type()), false);

        // do type check
        var results = ImmutableTreeSeq.from(elements.map(element -> inherit(element, elementTy).wellTyped()));
        var type = new DataCall(def, 0, ImmutableSeq.of(elementTy));
        yield new Jdg.Default(new ListTerm(results, match.recog(), type), type);
      }
      case Expr.New(var classCall) -> {
        var wellTyped = synthesize(classCall);
        if (!(wellTyped.wellTyped() instanceof ClassCall call)) {
          yield fail(expr.data(), BadTypeError.classCon(state, classCall, wellTyped.wellTyped()));
        }

        // check whether the call is fully applied
        if (call.args().size() != call.ref().members().size()) {
          yield fail(expr.data(), new ClassError.NotFullyApplied(classCall));
        }

        yield new Jdg.Default(new NewTerm(call), call);
      }
      case Expr.Match(var discriminant, var clauses, var asBindings, var isElim, var returns) -> {
        var wellArgs = discriminant.map(this::synthesize);
        if (returns == null) yield fail(expr.data(), new MatchMissingReturnsError(expr));
        // Type check the type annotation
        Term type;
        if (asBindings.isEmpty()) type = ty(returns);
        else try (var _ = subscope()) {
          asBindings.forEachWith(wellArgs, (as, discr) -> localCtx().put(as, discr.type()));
          type = ty(returns).bindTele(asBindings.view());
        }
        yield new Jdg.Default(match(discriminant, expr.sourcePos(), clauses, wellArgs, type), type);
      }
      case Expr.Unresolved _ -> Panic.unreachable();
      default -> fail(expr.data(), new NoRuleError(expr, null));
    };
  }

  private @NotNull Jdg checkApplication(
    @NotNull Expr.Ref f, int lift, @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Expr.NamedArg> args
  ) {
    try {
      var result = doCheckApplication(sourcePos, f.var(), lift, args);
      addWithTerm(f, sourcePos, result.type());
      return result;
    } catch (NotPi notPi) {
      var expr = new Expr.App(new WithPos<>(sourcePos, f), args);
      return fail(expr, BadTypeError.appOnNonPi(state, new WithPos<>(sourcePos, expr), notPi.actual));
    }
  }

  private @NotNull Jdg doCheckApplication(
    @NotNull SourcePos sourcePos, @NotNull AnyVar f,
    int lift, @NotNull ImmutableSeq<Expr.NamedArg> args
  ) throws NotPi {
    return switch (f) {
      case LocalVar ref when localLet.contains(ref) ->
        ArgsComputer.generateApplication(this, args, localLet.get(ref)).lift(lift);
      case LocalVar lVar -> ArgsComputer.generateApplication(this, args,
        new Jdg.Default(new FreeTerm(lVar), localCtx().get(lVar))).lift(lift);
      case CompiledVar(var content) -> new AppTycker<>(this, sourcePos, args.size(), lift, (params, k) ->
        computeArgs(sourcePos, args, params, k)).checkCompiledApplication(content);
      case DefVar<?, ?> defVar -> new AppTycker<>(this, sourcePos, args.size(), lift, (params, k) ->
        computeArgs(sourcePos, args, params, k)).checkDefApplication(defVar);
      default -> throw new UnsupportedOperationException("TODO");
    };
  }

  private Jdg computeArgs(
    @NotNull SourcePos pos, @NotNull ImmutableSeq<Expr.NamedArg> args,
    @NotNull AbstractTele params, @NotNull BiFunction<Term[], @Nullable Term, Jdg> k
  ) throws NotPi {
    return new ArgsComputer(this, pos, args, params).boot(k);
  }

  /// Tyck a [Expr.Lambda] against to {@param type}
  ///
  /// @param unpier provides the telescope of PiTerm, can be shorter than required
  private @NotNull Term checkLam(
    @NotNull SourcePos pos,
    @NotNull Expr.Lambda lam,
    @NotNull Function<ImmutableSeq<LocalVar>, DepTypeTerm.Unpi> unpier
  ) {
    // FIXME: unify with Expr.Lambda#unlam
    var binds = lam.patterns().map(pat -> switch (pat.term().data()) {
      case Pattern.Bind bindPat -> bindPat.bind();
      case Pattern.CalmFace _ -> LocalVar.generate(Constants.ANONYMOUS_PREFIX, pat.term().sourcePos());
      default -> null;
    });

    // is the lambda a vanilla version?
    var isVanilla = binds.allMatch(Objects::nonNull);

    if (isVanilla) {
      var unpi = unpier.apply(binds);

      try (var _ = subscope()) {
        // load all binds
        binds.forEachWith(unpi.params(), (ref, param) ->
          localCtx().put(ref, param.type()));

        var paramSize = unpi.params().size();

        // check body
        WithPos<Expr> realBody = paramSize == lam.patterns().size()
          ? lam.body()
          // trigger a error report
          : new WithPos<>(pos, new Expr.Lambda(lam.clause().update(lam.clause().patterns.drop(paramSize), lam.clause().expr)));

        return inherit(realBody, unpi.body()).wellTyped();
      }
    } else {
      var teleVars = ImmutableSeq.fill(lam.patterns().size(), i -> LocalVar.generate("LambdaMatch" + i));
      var unpi = unpier.apply(teleVars);

      var worker = new ClauseTycker.Worker(
        new ClauseTycker(this),
        unpi.params(),
        new DepTypeTerm.Unpi(unpi.body()),
        teleVars,
        ImmutableSeq.empty(),
        ImmutableSeq.of(lam.clause())
      );

      var clsResult = worker.check(pos);
      if (clsResult.hasLhsError()) {
        return new ErrorTerm(lam);
      }

      // TODO: optimize leading bindings
      // TODO: make match call
      throw new UnsupportedOperationException("TODO");
    }
  }

  /**
   * tyck a let expr with the given checker
   *
   * @param checker check the type of the body of {@param let}
   */
  private @NotNull Jdg checkLet(@NotNull Expr.Let let, @NotNull Function<WithPos<Expr>, Jdg> checker) {
    // pushing telescopes into lambda params, for example:
    // `let f (x : A) : B x` is desugared to `let f : Pi (x : A) -> B x`
    var letBind = let.bind();
    var typeExpr = Expr.buildPi(letBind.sourcePos(),
      letBind.telescope().view(), letBind.result());
    // as well as the body of the binding, for example:
    // `let f x := g` is desugared to `let f := \x => g`
    var definedAsExpr = Expr.buildLam(letBind.sourcePos(),
      letBind.telescope().view().map(Expr.Param::ref), letBind.definedAs());

    // Now everything is in form `let f : G := g in h`

    var type = freezeHoles(ty(typeExpr));
    var definedAsResult = inherit(definedAsExpr, type);

    try (var _ = subscope()) {
      localLet.put(let.bind().bindName(), definedAsResult);
      return checker.apply(let.body());
    }
  }

  // region Overrides and public APIs
  @Override public @NotNull TermComparator unifier(@NotNull SourcePos pos, @NotNull Ordering order) {
    return new Unifier(state(), localCtx(), reporter(), pos, order, true);
  }

  public record SubscopedVar(
    @NotNull LocalCtx parentCtx,
    @NotNull LocalVar var,
    @NotNull ExprTycker tycker
  ) implements AutoCloseable {
    @Override public void close() {
      tycker.setLocalCtx(parentCtx);
      tycker.state.removeConnection(var);
    }
  }

  public record SubscopedNoVar(
    @NotNull LocalCtx parentCtx, @NotNull LocalLet parentDef,
    @NotNull ExprTycker tycker
  ) implements AutoCloseable {
    @Override public void close() {
      tycker.setLocalCtx(parentCtx);
      tycker.setLocalLet(parentDef);
      tycker.localCtx().extractLocal().forEach(tycker.state::removeConnection);
    }
  }

  public @NotNull SubscopedNoVar subscope() {
    return new SubscopedNoVar(
      setLocalCtx(localCtx().derive()),
      setLocalLet(localLet().derive()),
      this
    );
  }

  public @NotNull SubscopedVar subscope(@NotNull LocalVar var, @NotNull Term type) {
    return new SubscopedVar(setLocalCtx(localCtx().derive1(var, type)), var, this);
  }

  public @NotNull LocalLet localLet() { return localLet; }
  public @NotNull LocalLet setLocalLet(@NotNull LocalLet let) {
    var old = localLet;
    this.localLet = let;
    return old;
  }
  // endregion Overrides and public APIs

  protected static final class NotPi extends Exception {
    public final @NotNull Term actual;
    public NotPi(@NotNull Term actual) { this.actual = actual; }
  }
}

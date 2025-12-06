// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.ImmutableTreeSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableTreeSet;
import org.aya.generic.Constants;
import org.aya.generic.term.DTKind;
import org.aya.pretty.doc.Doc;
import org.aya.states.InstanceSet;
import org.aya.states.TyckState;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.def.DataDefLike;
import org.aya.syntax.core.def.Matchy;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.repr.AyaShape;
import org.aya.syntax.core.repr.ShapeRecognition;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.*;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.core.term.repr.ListTerm;
import org.aya.syntax.core.term.repr.MetaLitTerm;
import org.aya.syntax.core.term.repr.StringTerm;
import org.aya.syntax.core.term.xtt.*;
import org.aya.syntax.ref.*;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.tyck.error.*;
import org.aya.tyck.pat.ClauseTycker;
import org.aya.tyck.tycker.AppTycker;
import org.aya.util.Decision;
import org.aya.util.Ordering;
import org.aya.util.Panic;
import org.aya.util.position.SourceNode;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.function.Function;

public final class ExprTycker extends ScopedTycker {
  public final @NotNull MutableTreeSet<WithPos<Expr.WithTerm>> withTerms =
    MutableTreeSet.create(Comparator.comparing(SourceNode::sourcePos));
  public final @NotNull MutableList<WithPos<Expr.Hole>> userHoles = MutableList.create();
  private final @NotNull ModulePath fileModule;

  public void addWithTerm(@NotNull Expr.WithTerm with, @NotNull SourcePos pos, @NotNull Term type) {
    if (with.theCoreType() != Expr.WithTerm.DUMMY) {
      withTerms.add(new WithPos<>(pos, with));
      with.theCoreType().set(type);
    }
  }

  public ExprTycker(
    @NotNull TyckState state, @NotNull InstanceSet instanceSet,
    @NotNull Reporter reporter, @NotNull ModulePath fileModule
  ) {
    super(state, instanceSet, new MapLocalCtx(), reporter);
    this.fileModule = fileModule;
  }

  public void solveMetas() {
    state.solveMetas(reporter);
    withTerms.forEach(with -> with.data().theCoreType().update(this::freezeHoles));
    userHoles.forEach(hole -> hole.data().solution().update(this::freezeHoles));
  }

  /**
   * @param type may not be in whnf, because we want unnormalized type to be used for unification.
   */
  public @Closed @NotNull Jdg inherit(@NotNull WithPos<Expr> expr, @Closed @NotNull Term type) {
    return switch (expr.data()) {
      case Expr.Lambda lam -> {
        var ref = lam.ref();
        var body = lam.body();

        yield switch (whnf(type)) {
          case DepTypeTerm(var kind, var dom, var cod) when kind == DTKind.Pi -> {
            // unifyTyReported(param, dom, expr);
            try (var _ = subscope(ref, dom)) {
              addWithTerm(lam, expr.sourcePos(), dom);
              var core = inherit(body, cod.apply(new FreeTerm(ref))).wellTyped().bind(ref);
              yield new Jdg.Default(new LamTerm(core), type);
            }
          }
          case EqTerm eq -> {
            @Closed Closure.Locns core;
            try (var _ = subscope(ref, interval())) {
              addWithTerm(lam, expr.sourcePos(), interval());
              core = inherit(body, eq.appA(new FreeTerm(ref))).wellTyped().bind(ref);
            }
            var isOk = checkBoundaries(eq, core, body.sourcePos(), msg ->
              new CubicalError.BoundaryDisagree(expr, msg, new UnifyInfo(state)));
            var term = new LamTerm(core);
            yield new Jdg.Default(isOk ? term : new ErrorTerm(term), eq);
          }
          case MetaCall metaCall -> {
            var pi = metaCall.asDt(this::whnf, "_dom", "_cod", DTKind.Pi);
            if (pi == null) yield fail(expr.data(), type, BadTypeError.absOnNonPi(state, expr, type));
            unifier(metaCall.ref().pos(), Ordering.Eq).compare(metaCall, pi, null);
            try (var _ = subscope(ref, pi.param())) {
              addWithTerm(lam, expr.sourcePos(), pi.param());
              var core = inherit(body, pi.body().apply(new FreeTerm(ref))).wellTyped().bind(ref);
              yield new Jdg.Default(new LamTerm(core), pi);
            }
          }
          default -> fail(expr.data(), type, BadTypeError.absOnNonPi(state, expr, type));
        };
      }
      case Expr.Hole hole -> {
        var freshHole = freshMeta(Constants.randomName(hole), expr.sourcePos(),
          new MetaVar.OfType.Default(type), hole.explicit());
        hole.solution().set(freshHole);
        Jdg filling = null;
        if (hole.filling() != null) filling = synthesize(hole.filling());
        userHoles.append(new WithPos<>(expr.sourcePos(), hole));
        if (hole.explicit()) fail(new Goal(state, freshHole, filling, localCtx().copy(), hole.accessibleLocal()));
        yield new Jdg.Default(freshHole, type);
      }
      case Expr.LitInt(var end) -> {
        var ty = whnf(type);
        if (isInterval(ty)) {
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
      case Expr.Match(var discriminant, var clauses, var returns) -> {
        var wellArgs = discriminant.map(d -> synthesize(d.discr()));
        @Bound Term storedTy;
        // Type check the type annotation
        if (returns != null) {
          storedTy = matchReturnTy(discriminant, wellArgs, returns);

          var erase = storedTy;
          @Closed var instedTy = erase.instTele(wellArgs.view().map(Jdg::wellTyped));

          unifyTyReported(type, instedTy, returns);
        } else {
          storedTy = type;
        }
        yield new Jdg.Default(match(discriminant, expr.sourcePos(), clauses, wellArgs, storedTy), type);
      }
      case Expr.Let let -> checkLet(let, e -> inherit(e, type));
      case Expr.Partial(var clause) -> {
        if (!(whnf(type) instanceof PartialTyTerm(var A, var cof)))
          yield fail(expr.data(), type, BadTypeError.partialElement(state, expr, type));
        // check each clause
        ImmutableSeq<PartialTerm.@Closed Clause> cls = ImmutableSeq.empty();
        ImmutableSeq<@Closed ConjCofNF> all_cof = ImmutableSeq.empty();
        for (var rcls : clause) {
          var cls_cof = elabCof(rcls.cof());
          var cls_rhs = inherit(rcls.tm(), A).wellTyped();
          cls = cls.appended(new PartialTerm.Clause(cls_cof, cls_rhs));
          all_cof = all_cof.appended(cls_cof);
        }
        // coverage. cof <=> all_cof
        var disj = expand(cof);
        if (!(unifier(expr.sourcePos(), Ordering.Eq).cofibrationEquiv(disj, new DisjCofNF(all_cof))))
          yield fail(expr.data(), type, new IllegalPartialElement.CofMismatch(disj, new DisjCofNF(all_cof), expr.sourcePos(), state()));
        // boundary
        for (@Closed var c1 : cls)
          for (@Closed var c2 : cls) {
          if (c1 == c2) continue;
          if (!(withConnection(c1.cof().add(c2.cof().descent(whnfVisitor())),
              () -> unifier(expr.sourcePos(), Ordering.Eq).compare(c1.tm(), c2.tm(), A) == Decision.YES,
              () -> true)))
            yield fail(expr.data(), type, new IllegalPartialElement.ValueMismatch(c1, c2, expr.sourcePos(), state()));
        }
        yield new Jdg.Default(new PartialTerm(cls), type);
      }
      default -> inheritFallbackUnify(type, synthesize(expr), expr);
    };
  }

  private @Closed @NotNull EqCofTerm elabCof(@NotNull Expr.EqCof cof) {
    var lhs = inherit(cof.lhs(), interval());
    var rhs = inherit(cof.rhs(), interval());
    return new EqCofTerm(lhs.wellTyped(), rhs.wellTyped());
  }

  private @Closed @NotNull ConjCofNF elabCof(@NotNull Expr.ConjCof conj) {
    ImmutableSeq<@Closed EqCofTerm> ret = ImmutableSeq.empty();
    for (var c : conj.elements())
      ret = ret.appended(elabCof(c));
    return new ConjCofNF(ret);
  }

  private @Closed @NotNull DisjCofNF elabCof(@NotNull Expr.DisjCof disj) {
    ImmutableSeq<@Closed ConjCofNF> ret = ImmutableSeq.empty();
    for (var c : disj.elements())
      ret = ret.appended(elabCof(c));
    return new DisjCofNF(ret);
  }

  /// @return a [Bound] term where lives in [#wellArgs].size()-th db-level
  private @Bound @NotNull Term matchReturnTy(
    ImmutableSeq<Expr.Match.Discriminant> discriminant,
    ImmutableSeq<Jdg> wellArgs, WithPos<Expr> returns
  ) {
    try (var _ = subLocalCtx()) {
      var tele = MutableList.<LocalVar>create();
      wellArgs.forEachWith(discriminant, (arg, discr) -> {
        if (discr.asBinding() != null) {
          localCtx().put(discr.asBinding(), arg.type());
          tele.append(discr.asBinding());
        } else if (discr.isElim() && arg.wellTyped() instanceof FreeTerm(var name)) {
          tele.append(name);
        } else {
          tele.append(LocalVar.generate(discr.discr().sourcePos()));
        }
      });

      return ty(returns).bindTele(tele.view());
    }
  }

  private @NotNull MatchCall match(
    ImmutableSeq<Expr.Match.Discriminant> discriminant, @NotNull SourcePos exprPos,
    ImmutableSeq<Pattern.Clause> clauses, ImmutableSeq<Jdg> wellArgs, Term type
  ) {
    var elimVarTele = MutableList.<LocalVar>create();
    var paramTele = MutableList.<Param>create();
    wellArgs.forEachWith(discriminant, (arg, discr) -> {
      var paramTy = arg.type().bindTele(elimVarTele.view());

      if (discr.isElim() && arg.wellTyped() instanceof FreeTerm(var name)) {
        elimVarTele.append(name);
      } else {
        elimVarTele.append(LocalVar.generate(discr.discr().sourcePos()));
      }

      paramTele.append(new Param(Constants.ANONYMOUS_PREFIX, paramTy, true));
    });

    var clauseTycker = new ClauseTycker.Worker(
      new ClauseTycker(this),
      paramTele.toSeq(),
      new DepTypeTerm.Unpi(ImmutableSeq.empty(), type),
      elimVarTele.toSeq(),
      ImmutableSeq.empty(), clauses);
    var wellClauses = clauseTycker.check(exprPos).wellTyped().matchingsView();

    // Find free occurrences
    var usages = new FreeCollector();
    wellClauses.forEach(clause -> usages.apply(clause.body()));
    usages.apply(type);

    // Bind the free occurrences and spawn the lifted clauses as a definition
    var captures = usages.collected();
    var captureVars = captures.view().map(FreeTermLike::name);
    var lifted = new Matchy(type.bindTele(wellArgs.size(), captureVars),
      new QName(QPath.fileLevel(fileModule), "match-" + exprPos.lineColumnString()),
      wellClauses.map(clause -> clause.update(clause.body().bindTele(clause.bindCount(), captureVars)))
        .toSeq());

    var wellTerms = wellArgs.map(Jdg::wellTyped);
    return new MatchCall(lifted, wellTerms, ImmutableSeq.narrow(captures));
  }

  /**
   * @param type   expected type
   * @param result wellTyped + actual type from synthesize
   * @param expr   original expr, used for error reporting
   */
  private @NotNull Jdg inheritFallbackUnify(@Closed @NotNull Term type, @Closed @NotNull Jdg result, @NotNull WithPos<Expr> expr) {
    type = whnf(type);
    var resultType = result.type();
    // Try coercive subtyping for (Path A ...) into (I -> A)
    if (type instanceof DepTypeTerm(var kind, var dom, var cod) && kind == DTKind.Pi && isInterval(dom)) {
      if (whnf(resultType) instanceof EqTerm eq) {
        if (!isConvertiblePiPath(expr, eq, cod)) return makeErrorResult(type, result);
        var closure = result.wellTyped() instanceof LamTerm(var clos) ? clos
          : new Closure.Jit(i -> new PAppTerm(result.wellTyped(), i, eq.a(), eq.b()).make());
        return new Jdg.Default(new LamTerm(closure), eq);
      }
    }
    // Try coercive subtyping for (I -> A) into (Path A ...)
    if (type instanceof EqTerm eq) {
      if (whnf(resultType) instanceof DepTypeTerm(
        var kind, var dom, var cod
      ) && kind == DTKind.Pi && isInterval(dom)) {
        if (!isConvertiblePiPath(expr, eq, cod)) return makeErrorResult(type, result);
        @Closed var closure = result.wellTyped() instanceof LamTerm(var clos)
          ? clos    // closed cause [result] is closed
          : new Closure.Jit(i -> new AppTerm(result.wellTyped(), i));   // closed cause [result] is closed
        var isOk = checkBoundaries(eq, closure, expr.sourcePos(), msg ->
          new CubicalError.BoundaryDisagree(expr, msg, new UnifyInfo(state)));
        var resultTerm = new LamTerm(closure);
        return new Jdg.Default(isOk ? resultTerm : new ErrorTerm(resultTerm), eq);
      }
    }
    // Try coercive subtyping between classes
    if (type instanceof ClassCall clazz) {
      // Try coercive subtyping for `SomeClass (foo := 114514)` into `SomeClass`
      resultType = whnf(resultType);
      if (resultType instanceof ClassCall resultClazz) {
        // TODO: check whether resultClazz <: clazz
        if (resultClazz.ref().equals(clazz.ref())) {
          // No need to coerce
          if (clazz.args().size() == resultClazz.args().size()) return result;
          var forget = resultClazz.args().drop(clazz.args().size());
          return new Jdg.Default(ClassCastTerm.make(clazz.ref(), result.wellTyped(), clazz.args(), forget), type);
        } else {
          fail(new ClassError.DifferentClass(expr.sourcePos(),
            clazz, resultClazz));
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
  private boolean isConvertiblePiPath(@NotNull WithPos<Expr> expr, @Closed @NotNull EqTerm eq, @Closed @NotNull Closure cod) {
    var ref = new FreeTerm(new LocalVar("i"));
    var wellTyped = false;
    try (var _ = subscope(ref.name(), interval())) {
      wellTyped = unifyTyReported(eq.appA(ref), cod.apply(ref), expr);
    }
    if (!wellTyped) return false;
    if (expr.data() instanceof Expr.WithTerm with)
      addWithTerm(with, expr.sourcePos(), eq);
    return true;
  }

  public @Closed @NotNull Term ty(@NotNull WithPos<Expr> expr) {
    return switch (expr.data()) {
      case Expr.Hole hole -> {
        var meta = freshMeta(Constants.randomName(hole), expr.sourcePos(), MetaVar.Misc.IsType, hole.explicit());
        Jdg filling = null;
        if (hole.filling() != null) filling = synthesize(hole.filling());
        if (hole.explicit()) fail(new Goal(state, meta, filling, localCtx().copy(), hole.accessibleLocal()));
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
          if (whnf(result.type()) instanceof ClassCall clazzCall &&
          clazzCall.ref().classifyingIndex() != -1) {
            yield new MemberCall(result.wellTyped(),
              clazzCall.ref().classifyingField(), 0, ImmutableSeq.empty());
          }

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

  private @NotNull SortTerm sort(@NotNull WithPos<Expr> errorMsg, @Closed @NotNull Term term) {
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

  public @Closed @NotNull Jdg synthesize(@NotNull WithPos<Expr> expr) {
    var result = doSynthesize(expr);
    if (expr.data() instanceof Expr.WithTerm with) {
      addWithTerm(with, expr.sourcePos(), result.type());
    }
    return result;
  }

  public @Closed @NotNull Jdg doSynthesize(@NotNull WithPos<Expr> expr) {
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
          // This should be similar to Expr.Ref case, except a class instance is provided
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
        var elementTy = freshMeta(sort.name(), expr.sourcePos(), new MetaVar.OfType.Default(sort.type()), false);

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
      case Expr.Match(var discriminant, var clauses, var returns) -> {
        var wellArgs = discriminant.map(d -> synthesize(d.discr()));
        if (returns == null) yield fail(expr.data(), new MatchMissingReturnsError(expr));
        // Type check the type annotation
        var type = matchReturnTy(discriminant, wellArgs, returns);
        yield new Jdg.Default(match(discriminant, expr.sourcePos(), clauses, wellArgs, type), type);
      }
      case Expr.PartialTy(var A, var cof) -> {
        var wellA = synthesize(A);
        var wellCof = elabCof(cof);
        var wellTyped = new PartialTyTerm(wellA.wellTyped(), wellCof);
        yield new Jdg.Default(wellTyped, wellA.type());
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
      addWithTerm(f, sourcePos, result.headType);
      return result.result;
    } catch (NotPi notPi) {
      var expr = new Expr.App(new WithPos<>(sourcePos, f), args);
      return fail(expr, BadTypeError.appOnNonPi(state, new WithPos<>(sourcePos, expr), notPi.actual));
    }
  }

  record DoCheckApp(
    @NotNull Jdg result,
    @NotNull Term headType
  ) {}

  private @NotNull DoCheckApp doCheckApplication(
    @NotNull SourcePos sourcePos, @NotNull AnyVar f,
    int lift, @NotNull ImmutableSeq<Expr.NamedArg> args
  ) throws NotPi {
    return switch (f) {
      case LocalVar ref when localLet().contains(ref) -> {
        var definedAs = localLet().get(ref);
        var jdg = definedAs.definedAs();
        @Closed var term = definedAs.inline()
          ? jdg.wellTyped()
          : new LetFreeTerm(ref, jdg);
        @Closed var start = new Jdg.Default(term, jdg.type());
        var result = ArgsComputer.generateApplication(this, args, start).lift(lift);
        yield new DoCheckApp(result, jdg.type());
      }
      case LocalVar lVar -> {
        var headType = localCtx().get(lVar);
        @Closed var jdg = new Jdg.Default(new FreeTerm(lVar), headType);
        var result = ArgsComputer.generateApplication(this, args, jdg).lift(lift);
        yield new DoCheckApp(result, headType);
      }
      case CompiledVar(var content) -> new AppTycker<>(this, sourcePos, args.size(), lift, (params, k) ->
        computeArgs(sourcePos, args, params, k)).checkCompiledApplication(content);
      case DefVar<?, ?> defVar -> new AppTycker<>(this, sourcePos, args.size(), lift, (params, k) ->
        computeArgs(sourcePos, args, params, k)).checkDefApplication(defVar);
      default -> Panic.unreachable();
    };
  }

  private @NotNull DoCheckApp computeArgs(
    @NotNull SourcePos pos, @NotNull ImmutableSeq<Expr.NamedArg> args,
    @NotNull AbstractTele params, @NotNull AppTycker.TeleChecker k
  ) throws NotPi {
    var argsComputer = new ArgsComputer(this, pos, args, params);
    var result = argsComputer.boot(k);
    return new DoCheckApp(result, argsComputer.headType());
  }

  /**
   * tyck a let expr with the given checker
   *
   * @param checker check the type of the body of {@param let}
   */
  private @Closed @NotNull Jdg checkLet(@NotNull Expr.Let let, @NotNull Function<WithPos<Expr>, @Closed Jdg> checker) {
    // pushing telescopes into lambda params, for example:
    // `let f (x : A) : B x` is desugared to `let f : Pi (x : A) -> B x`
    var letBind = let.bind();
    var bindName = letBind.bindName();
    var typeExpr = Expr.buildPi(letBind.sourcePos(),
      letBind.telescope().view(), letBind.result());
    // as well as the body of the binding, for example:
    // `let f x := g` is desugared to `let f := \x => g`
    var definedAsExpr = Expr.buildLam(letBind.sourcePos(),
      letBind.telescope().view()
        // we use dummy `theCoreType`, as it will be set while tyck [typeExpr].
        .map(p -> Expr.UntypedParam.dummy(p.ref())),
      letBind.definedAs());

    // Now everything is in form `let f : G := g in h`

    var type = freezeHoles(ty(typeExpr));
    var definedAs = inherit(definedAsExpr, type);

    addWithTerm(letBind, letBind.sourcePos(), definedAs.type());

    try (var _ = subLocalLet()) {
      if (letBind.isClassCandidate()) {
        addLetBind(bindName, definedAs, false, true);
      } else {
        localLet().put(bindName, definedAs, false);
      }

      var result = checker.apply(let.body());
      var letFree = new LetFreeTerm(bindName, definedAs);
      var wellTypedLet = LetTerm.bind(letFree, result.wellTyped());
      var typeLet = LetTerm.bind(letFree, result.type());
      return new Jdg.Default(wellTypedLet, typeLet);
    }
  }

  protected static final class NotPi extends Exception {
    public final @NotNull Term actual;
    public NotPi(@NotNull Term actual) { this.actual = actual; }
  }
}

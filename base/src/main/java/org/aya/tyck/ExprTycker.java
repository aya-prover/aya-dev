// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableStack;
import kala.collection.mutable.MutableTreeSet;
import kala.control.Result;
import org.aya.generic.Constants;
import org.aya.syntax.compile.JitTele;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.def.DataDefLike;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.repr.AyaShape;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.core.term.repr.ListTerm;
import org.aya.syntax.core.term.repr.MetaLitTerm;
import org.aya.syntax.core.term.repr.StringTerm;
import org.aya.syntax.core.term.xtt.DimTerm;
import org.aya.syntax.core.term.xtt.DimTyTerm;
import org.aya.syntax.core.term.xtt.EqTerm;
import org.aya.syntax.ref.*;
import org.aya.tyck.ctx.LocalLet;
import org.aya.tyck.error.*;
import org.aya.tyck.tycker.AbstractTycker;
import org.aya.tyck.tycker.AppTycker;
import org.aya.tyck.tycker.Unifiable;
import org.aya.unify.TermComparator;
import org.aya.unify.Unifier;
import org.aya.util.Ordering;
import org.aya.util.Pair;
import org.aya.util.error.Panic;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ExprTycker extends AbstractTycker implements Unifiable {
  public final @NotNull MutableTreeSet<WithPos<Expr.WithTerm>> withTerms =
    MutableTreeSet.create(Comparator.comparing(SourceNode::sourcePos));
  private @NotNull LocalLet localLet;

  public void addWithTerm(@NotNull Expr.WithTerm with, @NotNull SourcePos pos, @NotNull Term type) {
    withTerms.add(new WithPos<>(pos, with));
    with.theCoreType().set(type);
  }

  public ExprTycker(
    @NotNull TyckState state, @NotNull LocalCtx ctx, @NotNull LocalLet let,
    @NotNull Reporter reporter
  ) {
    super(state, ctx, reporter);
    this.localLet = let;
  }

  public ExprTycker(@NotNull TyckState state, @NotNull Reporter reporter) {
    this(state, new LocalCtx(), new LocalLet(), reporter);
  }

  public void solveMetas() {
    state.solveMetas(reporter);
    withTerms.forEach(with -> with.data().theCoreType().update(this::freezeHoles));
  }

  /**
   * @param type may not be in whnf, because we want unnormalized type to be used for unification.
   */
  public @NotNull Jdg inherit(@NotNull WithPos<Expr> expr, @NotNull Term type) {
    return switch (expr.data()) {
      case Expr.Lambda(var ref, var body) -> switch (type) {
        case PiTerm(var dom, var cod) -> {
          // unifyTyReported(param, dom, expr);
          var core = subscoped(() -> {
            localCtx().put(ref, dom);
            return inherit(body, cod.apply(new FreeTerm(ref))).wellTyped();
          }).bind(ref);
          yield new Jdg.Default(new LamTerm(core), type);
        }
        case EqTerm eq -> {
          var core = subscoped(() -> {
            localCtx().put(ref, DimTyTerm.INSTANCE);
            return inherit(body, eq.appA(new FreeTerm(ref))).wellTyped();
          }).bind(ref);
          checkBoundaries(eq, core, body.sourcePos(), msg ->
            new CubicalError.BoundaryDisagree(expr, msg, new UnifyInfo(state)));
          yield new Jdg.Default(new LamTerm(core), eq);
        }
        default -> fail(expr.data(), type, BadTypeError.pi(state, expr, type));
      };
      case Expr.Hole hole -> {
        var freshHole = freshMeta(Constants.randomName(hole), expr.sourcePos(), new MetaVar.OfType(type));
        if (hole.explicit()) fail(new Goal(state, freshHole, hole.accessibleLocal()));
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
      case Expr.Tuple(var elems) when type instanceof SigmaTerm sigmaTerm -> {
        Term wellTyped = switch (sigmaTerm.check(elems, (elem, ty) -> inherit(elem, ty).wellTyped())) {
          case Result.Ok(var v) -> new TupTerm(v);
          case Result.Err(var e) -> switch (e) {
            case TooManyElement, TooManyParameter -> {
              fail(new TupleError.ElemMismatchError(expr.sourcePos(), sigmaTerm.params().size(), elems.size()));
              yield new ErrorTerm(expr.data());
            }
            case CheckFailed -> Panic.unreachable();
          };
        };
        yield new Jdg.Default(wellTyped, sigmaTerm);
      }
      case Expr.Let let -> checkLet(let, e -> inherit(e, type));
      default -> inheritFallbackUnify(type, synthesize(expr), expr);
    };
  }

  // TODO: coercive subtyping if needed
  private @NotNull Jdg inheritFallbackUnify(@NotNull Term type, @NotNull Jdg result, @NotNull WithPos<Expr> expr) {
    unifyTyReported(type, result.type(), expr);
    return result;
  }

  public @NotNull Term ty(@NotNull WithPos<Expr> expr) {
    return switch (expr.data()) {
      case Expr.Hole hole -> {
        var meta = freshMeta(Constants.randomName(hole), expr.sourcePos(), MetaVar.Misc.IsType);
        if (hole.explicit()) fail(new Goal(state, meta, hole.accessibleLocal()));
        yield meta;
      }
      case Expr.Sort sort -> new SortTerm(sort.kind(), sort.lift());
      case Expr.Pi(var param, var last) -> {
        var wellParam = ty(param.typeExpr());
        addWithTerm(param, param.sourcePos(), wellParam);
        yield subscoped(() -> {
          localCtx().put(param.ref(), wellParam);
          var wellLast = ty(last).bind(param.ref());
          return new PiTerm(wellParam, wellLast);
        });
      }
      case Expr.Sigma(var elems) -> subscoped(() -> {
        var tele = MutableList.<LocalVar>create();
        return new SigmaTerm(elems.map(elem -> {
          var result = ty(elem.typeExpr());
          var boundResult = result.bindTele(tele.view());
          localCtx().put(elem.ref(), result);
          tele.append(elem.ref());
          return boundResult;
        }));
      });
      case Expr.Let let -> checkLet(let, e -> lazyJdg(ty(e))).wellTyped();
      default -> synthesize(expr).wellTyped();
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
        fail(BadTypeError.univ(state, errorMsg, term));
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
      case Expr.Sugar s ->
        throw new IllegalArgumentException(STR."\{s.getClass()} is desugared, should be unreachable");
      case Expr.App(var f, var a) -> {
        if (!(f.data() instanceof Expr.Ref(var ref, _))) throw new IllegalStateException("function must be Expr.Ref");
        yield checkApplication(ref, expr.sourcePos(), a);
      }
      case Expr.Hole hole -> throw new UnsupportedOperationException("TODO");
      case Expr.Lambda lam -> inherit(expr, generatePi(lam, expr.sourcePos()));
      case Expr.LitInt(var integer) -> {
        // TODO[literal]: int literals. Currently the parser does not allow negative literals.
        var defs = state.shapeFactory().findImpl(AyaShape.NAT_SHAPE);
        if (defs.isEmpty()) yield fail(expr.data(), new NoRuleError(expr, null));
        if (defs.sizeGreaterThan(1)) {
          var type = freshMeta(STR."_ty\{integer}'", expr.sourcePos(), MetaVar.Misc.IsType);
          yield new Jdg.Default(new MetaLitTerm(expr.sourcePos(), integer, defs, type), type);
        }
        var match = defs.getFirst();
        var type = new DataCall((DataDefLike) match.def(), 0, ImmutableSeq.empty());
        yield new Jdg.Default(new IntegerTerm(integer, match.recog(), type), type);
      }
      case Expr.LitString litStr -> {
        if (!state.primFactory().have(PrimDef.ID.STRING))
          yield fail(litStr, new NoRuleError(expr, null));
        yield new Jdg.Default(new StringTerm(litStr.string()), state.primFactory().getCall(PrimDef.ID.STRING));
      }
      case Expr.Ref(var ref, _) -> checkApplication(ref, expr.sourcePos(), ImmutableSeq.empty());
      case Expr.Sigma _, Expr.Pi _ -> lazyJdg(ty(expr));
      case Expr.Sort _ -> sort(expr);
      case Expr.Tuple(var items) -> {
        var results = items.map(this::synthesize);
        var wellTypeds = results.map(Jdg::wellTyped);
        var tys = results.map(Jdg::type);
        var wellTyped = new TupTerm(wellTypeds);
        var ty = new SigmaTerm(tys);

        yield new Jdg.Default(wellTyped, ty);
      }
      case Expr.Let let -> checkLet(let, this::synthesize);
      case Expr.Error err -> new Jdg.Default(new ErrorTerm(err), ErrorTerm.typeOf(err));
      case Expr.Array arr when arr.arrayBlock().isRight() -> {
        var arrayBlock = arr.arrayBlock().getRightValue();
        var elements = arrayBlock.exprList();

        // find def
        var defs = state.shapeFactory().findImpl(AyaShape.LIST_SHAPE);
        if (defs.isEmpty()) yield fail(arr, new NoRuleError(expr, null));
        if (defs.sizeGreaterThan(1)) {
          var tyMeta = freshMeta("arr_ty", expr.sourcePos(), MetaVar.Misc.IsType);
          var results = elements.map(element -> inherit(element, tyMeta).wellTyped());
          yield new Jdg.Default(new MetaLitTerm(expr.sourcePos(), results, defs, tyMeta), tyMeta);
        }
        var match = defs.getFirst();
        var def = (DataDefLike) match.def();

        // List (A : Type)
        var sort = TyckDef.defSignature(def).telescopeRich(0);
        // the sort of type below.
        var elementTy = freshMeta(sort.name(), expr.sourcePos(), new MetaVar.OfType(sort.type()));

        // do type check
        var results = elements.map(element -> inherit(element, elementTy).wellTyped());
        var type = new DataCall(def, 0, ImmutableSeq.of(elementTy));
        yield new Jdg.Default(new ListTerm(results, match.recog(), type), type);
      }
      case Expr.Unresolved _ -> Panic.unreachable();
      default -> fail(expr.data(), new NoRuleError(expr, null));
    };
  }

  private @NotNull Jdg checkApplication(
    @NotNull AnyVar f, @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Expr.NamedArg> args
  ) {
    try {
      return doCheckApplication(f, args);
    } catch (NotPi notPi) {
      var expr = new Expr.App(new WithPos<>(sourcePos, new Expr.Ref(f)), args);
      return fail(expr, BadTypeError.pi(state, new WithPos<>(sourcePos, expr), notPi.actual));
    }
  }

  private @NotNull Jdg doCheckApplication(
    @NotNull AnyVar f, @NotNull ImmutableSeq<Expr.NamedArg> args
  ) throws NotPi {
    return switch (f) {
      case LocalVar ref when localLet.contains(ref) ->
        generateApplication(args, localLet.get(ref));
      case LocalVar lVar -> generateApplication(args,
        new Jdg.Default(new FreeTerm(lVar), localCtx().get(lVar)));
      case CompiledVar(var content) -> AppTycker.checkCompiledApplication(content,
        (params, k) -> computeArgs(args, params, k));
      case DefVar<?, ?> defVar -> AppTycker.checkDefApplication(defVar, state,
        (params, k) -> computeArgs(args, params, k));
      default -> throw new UnsupportedOperationException("TODO");
    };
  }

  private Jdg computeArgs(
    @NotNull ImmutableSeq<Expr.NamedArg> args,
    JitTele params, Function<Term[], Jdg> k
  ) throws NotPi {
    int argIx = 0, paramIx = 0;
    var result = new Term[params.telescopeSize];
    while (argIx < args.size() && paramIx < params.telescopeSize) {
      var arg = args.get(argIx);
      var param = params.telescopeRich(paramIx, result);
      // Implicit insertion
      if (arg.explicit() != param.explicit()) {
        if (!arg.explicit()) {
          fail(new LicitError.BadImplicitArg(arg));
          break;
        } else if (arg.name() == null) {
          // here, arg.explicit() == true and param.explicit() == false
          result[paramIx++] = mockTerm(param, arg.sourcePos());
          continue;
        }
      }
      if (arg.name() != null && !param.nameEq(arg.name())) {
        result[paramIx++] = mockTerm(param, arg.sourcePos());
        continue;
      }
      result[paramIx++] = inherit(arg.arg(), param.type()).wellTyped();
      argIx++;
    }
    // Trailing implicits
    while (paramIx < params.telescopeSize) {
      if (params.telescopeLicit[paramIx]) break;
      var param = params.telescopeRich(paramIx, result);
      result[paramIx++] = mockTerm(param, SourcePos.NONE);
    }
    var extraParams = MutableStack.<Pair<LocalVar, Term>>create();
    if (argIx < args.size()) {
      return generateApplication(args.drop(argIx), k.apply(result));
    } else while (paramIx < params.telescopeSize) {
      var param = params.telescopeRich(paramIx, result);
      var atarashiVar = LocalVar.generate(param.name());
      extraParams.push(new Pair<>(atarashiVar, param.type()));
      result[paramIx++] = new FreeTerm(atarashiVar);
    }
    var generated = k.apply(result);
    while (extraParams.isNotEmpty()) {
      var pair = extraParams.pop();
      generated = new Jdg.Default(
        new LamTerm(generated.wellTyped().bind(pair.component1())),
        new PiTerm(pair.component2(), generated.type().bind(pair.component1()))
      );
    }
    return generated;
  }

  private Jdg generateApplication(@NotNull ImmutableSeq<Expr.NamedArg> args, Jdg start) throws NotPi {
    return args.foldLeftChecked(start, (acc, arg) -> {
      if (arg.name() != null || !arg.explicit()) fail(new LicitError.BadNamedArg(arg));
      switch (whnf(acc.type())) {
        case PiTerm(var param, var body) -> {
          var wellTy = inherit(arg.arg(), param).wellTyped();
          return new Jdg.Default(AppTerm.make(acc.wellTyped(), wellTy), body.apply(wellTy));
        }
        case EqTerm eq -> {
          var wellTy = inherit(arg.arg(), DimTyTerm.INSTANCE).wellTyped();
          return new Jdg.Default(eq.makePApp(acc.wellTyped(), wellTy), eq.appA(wellTy));
        }
        case Term otherwise -> throw new NotPi(otherwise);
      }
    });
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
      letBind.telescope().view(), letBind.definedAs());

    // Now everything is in form `let f : G := g in h`

    var type = freezeHoles(ty(typeExpr));
    var definedAsResult = inherit(definedAsExpr, type);

    return subscoped(() -> {
      localLet.put(let.bind().bindName(), definedAsResult);
      return checker.apply(let.body());
    });
  }

  /// region Overrides and public APIs
  @Override public @NotNull TermComparator unifier(@NotNull SourcePos pos, @NotNull Ordering order) {
    return new Unifier(state(), localCtx(), reporter(), pos, order, true);
  }
  @Override @Contract(mutates = "this")
  public <R> R subscoped(@NotNull Supplier<R> action) {
    var parentCtx = setLocalCtx(localCtx().derive());
    var parentDef = setLocalLet(localLet.derive());
    var result = action.get();
    setLocalCtx(parentCtx);
    setLocalLet(parentDef);
    return result;
  }
  public @NotNull LocalLet localLet() { return localLet; }
  public @NotNull LocalLet setLocalLet(@NotNull LocalLet let) {
    var old = localLet;
    this.localLet = let;
    return old;
  }
  /// endregion Overrides and public APIs

  protected static final class NotPi extends Exception {
    public final @NotNull Term actual;
    public NotPi(@NotNull Term actual) { this.actual = actual; }
  }
}

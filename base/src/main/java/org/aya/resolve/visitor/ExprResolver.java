// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableStack;
import kala.value.MutableValue;
import org.aya.generic.stmt.TyckOrder;
import org.aya.generic.stmt.TyckUnit;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.context.NoExportContext;
import org.aya.resolve.error.GeneralizedNotAvailableError;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.decl.DataCon;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.GeneralizedVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.error.ClassError;
import org.aya.util.Panic;
import org.aya.util.position.PosedUnaryOperator;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.aya.util.reporter.LocalReporter;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves bindings.
 *
 * @param allowedGeneralizes will be filled with generalized vars if {@param allowGeneralizing},
 *                           and represents the allowed generalized level vars otherwise
 * @param allowGeneralizing  allow new generalized vars to be introduced
 * @author re-xyr, ice1000
 * @implSpec allowedGeneralizes must be linked map
 * @see StmtResolver
 */
public record ExprResolver(
  @NotNull Context ctx,
  @NotNull Reporter reporter,
  boolean allowGeneralizing,
  @NotNull MutableMap<GeneralizedVar, Expr.Param> allowedGeneralizes,
  @NotNull MutableList<TyckOrder> reference,
  @NotNull MutableStack<Where> where
) implements PosedUnaryOperator<Expr> {
  public record LiterateResolved(
    ImmutableSeq<Expr.Param> params,
    WithPos<Expr> expr
  ) {
    public @NotNull LiterateResolved descent(@NotNull PosedUnaryOperator<Expr> desalt) {
      return new LiterateResolved(params.map(p -> p.descent(desalt)), expr.descent(desalt));
    }
  }
  /**
   * TODO: check all caller for @Nullable
   * Do !!!NOT!!! use in the type checker.
   * This is solely for cosmetic features, such as literate mode inline expressions, or repl.
   */
  @Contract(pure = true)
  public static @Nullable LiterateResolved
  resolveLax(@NotNull ModuleContext context, @NotNull Reporter reporter, @NotNull WithPos<Expr> expr) {
    var localReporter = new LocalReporter(reporter);
    var resolver = new ExprResolver(context, localReporter, true);
    resolver.enter(Where.FnBody);
    var inner = expr.descent(resolver);
    if (localReporter.dirty()) return null;
    var view = resolver.allowedGeneralizes.valuesView().toSeq();
    return new LiterateResolved(view, inner);
  }

  public ExprResolver(
    @NotNull Context ctx, @NotNull Reporter reporter,
    boolean allowGeneralizing
  ) {
    this(ctx, reporter, allowGeneralizing, MutableLinkedHashMap.of(), MutableList.create(), MutableStack.create());
  }

  public void resetRefs() { reference.clear(); }
  public void enter(Where loc) { where.push(loc); }
  public void exit() { where.pop(); }

  public @NotNull ExprResolver enter(Context ctx) {
    return ctx == ctx() ? this : new ExprResolver(ctx, reporter, allowGeneralizing, allowedGeneralizes, reference, where);
  }

  /**
   * The intended usage is to create an {@link ExprResolver}
   * that resolves the body/bodies of something.
   */
  public @NotNull ExprResolver deriveRestrictive() {
    return new ExprResolver(ctx, reporter, false, allowedGeneralizes, reference, where);
  }

  public @NotNull Expr pre(@NotNull Expr expr) {
    return switch (expr) {
      case Expr.Proj(var tup, var ix, _, var theCore) -> {
        if (ix.isLeft()) yield expr;
        var projName = ix.getRightValue();
        var resolvedIx = ctx.getMaybe(projName, reporter);
        if (resolvedIx == null) {
          reporter.report(new ClassError.UnknownMember(projName.sourcePos(), projName.join()));
          yield expr;
        }
        if (resolvedIx.isEmpty()) yield expr;
        yield new Expr.Proj(tup, ix, resolvedIx.get(), theCore);
      }
      case Expr.Hole(var expl, var fill, var core, var local) -> {
        assert local.isEmpty();
        yield new Expr.Hole(expl, fill, core,
          ctx.collect(MutableList.create()).toSeq());
      }
      default -> expr;
    };
  }

  /**
   * Special handling of terms with binding structure.
   * We need to invoke a resolver with a different context under the binders.
   */
  @Override public @NotNull Expr apply(@NotNull SourcePos pos, @NotNull Expr expr) {
    return switch (pre(expr)) {
      case Expr.Do doExpr ->
        doExpr.update(apply(SourcePos.NONE, doExpr.bindName()), bind(doExpr.binds(), MutableValue.create(ctx)));
      case Expr.ClauseLam lam -> lam.update(clause(ImmutableSeq.empty(), lam.clause(), reporter));
      case Expr.DepType depType -> {
        var mCtx = MutableValue.create(ctx);
        var param = bind(depType.param(), mCtx);
        yield depType.update(param, depType.last().descent(enter(mCtx.get())));
      }
      case Expr.Array array -> array.update(array.arrayBlock().map(
        left -> {
          var mCtx = MutableValue.create(ctx);
          var binds = bind(left.binds(), mCtx);
          var generator = left.generator().descent(enter(mCtx.get()));
          return left.update(generator, binds, left.names().fmap(this::forceApply));
        },
        right -> right.descent(this)
      ));
      case Expr.Unresolved(var name) -> {
        var resolved = resolve(name);
        AnyVar finalVar = switch (resolved) {
          case GeneralizedVar generalized -> {
            // a "resolved" GeneralizedVar is not in [allowedGeneralizes]
            if (allowGeneralizing) {
              // Ordered set semantics. Do not expect too many generalized vars.
              var owner = generalized.owner;
              assert owner != null : "Sanity check";
              var param = owner.toExpr(false, generalized.toLocal());
              allowedGeneralizes.put(generalized, param);
              addReference(owner);
              yield param.ref();
            } else {
              reporter.report(new GeneralizedNotAvailableError(pos, generalized));
              yield null;
            }
          }
          case DefVar<?, ?> defVar -> {
            addReference(defVar);
            yield defVar;
          }
          case AnyVar var -> var;
          case null -> null;
        };

        if (finalVar == null) {
          yield expr;
        }

        yield new Expr.Ref(finalVar);
      }
      case Expr.Let let -> {
        // resolve letBind
        var letBind = let.bind();

        var mCtx = MutableValue.create(ctx);
        // visit telescope
        var telescope = letBind.telescope().map(param -> bind(param, mCtx));
        // for things that can refer the telescope (like result and definedAs)
        var resolver = enter(mCtx.get());
        // visit result
        var result = letBind.result().descent(resolver);
        // visit definedAs
        var definedAs = letBind.definedAs().descent(resolver);
        // end resolve letBind

        // resolve body
        var newBody = let.body().descent(enter(ctx.bind(letBind.bindName(), reporter)));

        yield let.update(letBind.update(telescope, result, definedAs), newBody);
      }
      case Expr.LetOpen letOpen -> {
        var context = new NoExportContext(ctx);
        // open module
        context.openModule(letOpen.componentName().data(), Stmt.Accessibility.Private,
          letOpen.sourcePos(), letOpen.useHide(), reporter);

        yield letOpen.update(letOpen.body().descent(enter(context)));
      }
      case Expr.Match match -> {
        var discriminant = match.discriminant().map(d -> d.descent(this));
        var returnsCtx = ctx;
        for (var discr : match.discriminant()) {
          if (discr.asBinding() != null) {
            returnsCtx = returnsCtx.bind(discr.asBinding(), reporter);
          }
        }
        var returns = match.returns() != null ? match.returns().descent(enter(returnsCtx)) : null;

        // Requires exhaustiveness check, therefore must need the full data body
        enter(Where.FnPattern);
        var clauses = match.clauses().map(x -> clause(ImmutableSeq.empty(), x, reporter));
        exit();

        yield match.update(discriminant, clauses, returns);
      }

      // Expr.Lambda is a desugar target, which is produced after resolving.
      case Expr.Lambda _ -> Panic.unreachable();
      case Expr newExpr -> newExpr.descent(this);
    };
  }

  private void addReference(@NotNull TyckUnit unit) {
    if (where.isEmpty()) throw new Panic("where am I?");
    switch (where.peek()) {
      case FnPattern -> {
        reference.append(new TyckOrder.Body(unit));
        if (unit instanceof DataCon con) {
          reference.append(new TyckOrder.Body(con.dataRef.concrete));
        }
      }
      default -> reference.append(new TyckOrder.Head(unit));
    }
  }

  private void addReference(@NotNull DefVar<?, ?> defVar) {
    addReference(defVar.concrete);
  }

  public @NotNull Pattern.Clause clause(@NotNull ImmutableSeq<LocalVar> telescope, @NotNull Pattern.Clause clause, @NotNull Reporter reporter) {
    var mCtx = MutableValue.create(ctx);
    enter(Where.FnPattern);
    var pats = clause.patterns.map(pa ->
      pa.descent(pat -> resolvePattern(pat, telescope, mCtx, reporter)));
    exit();
    enter(Where.FnBody);
    var body = clause.expr.map(x -> x.descent(enter(mCtx.get())));
    exit();
    return clause.update(pats, body);
  }

  /// Resolve a [Pattern]
  ///
  /// @param telescope the telescope of the clause which the {@param pattern} lives, can be [ImmutableSeq#empty()].
  public @NotNull WithPos<Pattern> resolvePattern(@NotNull WithPos<Pattern> pattern, @NotNull ImmutableSeq<LocalVar> telescope, MutableValue<Context> ctx, @NotNull Reporter reporter) {
    var resolver = new PatternResolver(ctx.get(), telescope, this::addReference, reporter);
    var result = pattern.descent(resolver);
    ctx.set(resolver.context());
    return result;
  }

  @Contract(mutates = "param2")
  public @NotNull Expr.Param bind(@NotNull Expr.Param param, @NotNull MutableValue<Context> ctx) {
    var p = param.descent(enter(ctx.get()));
    ctx.set(ctx.get().bind(param.ref(), reporter));
    return p;
  }

  public @NotNull ImmutableSeq<Expr.DoBind>
  bind(@NotNull ImmutableSeq<Expr.DoBind> binds, @NotNull MutableValue<Context> ctx) {
    return binds.map(bind -> {
      var b = bind.descent(enter(ctx.get()));
      ctx.set(ctx.get().bind(bind.var(), reporter));
      return b;
    });
  }

  public @Nullable AnyVar resolve(@NotNull QualifiedID name) {
    var result = ctx.get(name, reporter);
    if (result instanceof GeneralizedVar gvar) {
      var gened = allowedGeneralizes.getOrNull(gvar);
      if (gened != null) return gened.ref();
    }

    return result;
  }

  public @NotNull ExprResolver member(@NotNull TyckUnit decl, Where initial) {
    var resolver = new ExprResolver(ctx, reporter, false, allowedGeneralizes,
      MutableList.of(new TyckOrder.Head(decl)),
      MutableStack.create());
    resolver.enter(initial);
    return resolver;
  }

  public enum Where {
    // Data head & Fn head
    Head,
    // Con patterns
    ConPattern,
    // Functions with just a body
    FnSimple,
    // Fn patterns
    FnPattern,
    // Body of non-simple functions
    FnBody
  }
  public record Options(boolean allowIntroduceGeneralized) { }
}

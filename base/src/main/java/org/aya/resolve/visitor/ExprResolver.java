// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
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
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.decl.DataCon;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.GeneralizedVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.error.Panic;
import org.aya.util.error.PosedUnaryOperator;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static org.aya.syntax.concrete.Expr.buildLam;

/**
 * Resolves bindings.
 *
 * @param allowedGeneralizes will be filled with generalized vars if {@link Options#allowIntroduceGeneralized},
 *                           and represents the allowed generalized level vars otherwise
 * @author re-xyr, ice1000
 * @implSpec allowedGeneralizes must be linked map
 * @see StmtResolver
 */
public record ExprResolver(
  @NotNull Context ctx,
  @NotNull Options options,
  @NotNull MutableMap<GeneralizedVar, Expr.Param> allowedGeneralizes,
  @NotNull MutableList<TyckOrder> reference,
  @NotNull MutableStack<Where> where
) implements PosedUnaryOperator<Expr> {
  /**
   * Do !!!NOT!!! use in the type checker.
   * This is solely for cosmetic features, such as literate mode inline expressions, or repl.
   */
  @Contract(pure = true)
  public static WithPos<Expr> resolveLax(@NotNull ModuleContext context, @NotNull WithPos<Expr> expr) {
    var resolver = new ExprResolver(context, ExprResolver.LAX);
    resolver.enter(Where.FnBody);
    var inner = expr.descent(resolver);
    var view = resolver.allowedGeneralizes().valuesView().toImmutableSeq().view();
    return buildLam(expr.sourcePos(), view, inner);
  }

  public ExprResolver(@NotNull Context ctx, @NotNull Options options) {
    this(ctx, options, MutableLinkedHashMap.of(), MutableList.create(), MutableStack.create());
  }

  public static final @NotNull Options RESTRICTIVE = new Options(false);
  public static final @NotNull Options LAX = new Options(true);

  public void resetRefs() { reference.clear(); }
  public void enter(Where loc) { where.push(loc); }
  public void exit() { where.pop(); }

  public @NotNull ExprResolver enter(Context ctx) {
    return ctx == ctx() ? this : new ExprResolver(ctx, options, allowedGeneralizes, reference, where);
  }

  /**
   * The intended usage is to create an {@link ExprResolver}
   * that resolves the body/bodies of something.
   */
  public @NotNull ExprResolver deriveRestrictive() {
    return new ExprResolver(ctx, RESTRICTIVE,
      // Hoshino: we needn't copy {allowedGeneralizes} cause this resolver is RESTRICTIVE
      allowedGeneralizes, reference, where);
  }

  public @NotNull Expr pre(@NotNull Expr expr) {
    return switch (expr) {
      case Expr.Proj(var tup, var ix, _, var theCore) -> {
        if (ix.isLeft()) yield expr;
        var projName = ix.getRightValue();
        var resolvedIx = ctx.getMaybe(projName);
        // TODO: require Record things
        // if (resolvedIx == null) ctx.reportAndThrow(new FieldError.UnknownField(projName.sourcePos(), projName.join()));
        yield new Expr.Proj(tup, ix, resolvedIx, theCore);
      }
      case Expr.Hole(var expl, var fill, var local) -> {
        assert local.isEmpty();
        yield new Expr.Hole(expl, fill, ctx.collect(MutableList.create()).toImmutableSeq());
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
//      case Expr.Match match -> {
//        var clauses = match.clauses().map(this::apply);
//        yield match.update(match.discriminant().map(this), clauses);
//      }
//      case Expr.New neu -> neu.update(apply(neu.struct()), neu.fields().map(field -> {
//        var fieldCtx = field.bindings().foldLeft(ctx, (c, x) -> c.bind(x.data()));
//        return field.descent(enter(fieldCtx));
//      }));
      case Expr.Lambda lam -> {
        var mCtx = MutableValue.create(ctx);
        mCtx.update(ctx -> bindAs(lam.ref(), ctx));
        yield lam.update(lam.body().descent(enter(mCtx.get())));
      }
      case Expr.Pi pi -> {
        var mCtx = MutableValue.create(ctx);
        var param = bind(pi.param(), mCtx);
        yield pi.update(param, pi.last().descent(enter(mCtx.get())));
      }
      case Expr.Sigma sigma -> {
        var mCtx = MutableValue.create(ctx);
        var params = sigma.params().map(param -> bind(param, mCtx));
        yield sigma.update(params);
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
      case Expr.Unresolved(var name) -> switch (ctx.get(name)) {
        case GeneralizedVar generalized -> {
          if (!allowedGeneralizes.containsKey(generalized)) {
            if (options.allowIntroduceGeneralized) {
              // Ordered set semantics. Do not expect too many generalized vars.
              var owner = generalized.owner;
              assert owner != null : "Sanity check";
              allowedGeneralizes.put(generalized, owner.toExpr(false, generalized.toLocal()));
              addReference(owner);
            } else {
              ctx.reportAndThrow(new GeneralizedNotAvailableError(pos, generalized));
            }
          }
          yield new Expr.Ref(allowedGeneralizes.get(generalized).ref());
        }
        case DefVar<?, ?> def -> {
          addReference(def);
          yield new Expr.Ref(def);
        }
        case AnyVar var -> new Expr.Ref(var);
      };
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
        var newBody = let.body().descent(enter(ctx.bind(letBind.bindName())));

        yield let.update(
          letBind.update(telescope, result, definedAs),
          newBody
        );
      }
      case Expr.LetOpen letOpen -> {
        var context = new NoExportContext(ctx);
        // open module
        context.openModule(letOpen.componentName(), Stmt.Accessibility.Private,
          letOpen.sourcePos(), letOpen.useHide());
        yield letOpen.update(letOpen.body().descent(enter(context)));
      }
      case Expr newExpr -> newExpr.descent(this);
    };
  }

  private void addReference(@NotNull TyckUnit unit) {
    if (where.isEmpty()) throw new Panic("where am I?");
    switch (where.peek()) {
      default -> reference.append(new TyckOrder.Head(unit));
      case FnPattern -> {
        reference.append(new TyckOrder.Body(unit));
        if (unit instanceof DataCon con) {
          reference.append(new TyckOrder.Body(con.dataRef.concrete));
        }
      }
    }
  }

  private void addReference(@NotNull DefVar<?, ?> defVar) {
    addReference(defVar.concrete);
  }

  public @NotNull Pattern.Clause clause(@NotNull Pattern.Clause clause) {
    var mCtx = MutableValue.create(ctx);
    enter(Where.FnPattern);
    var pats = clause.patterns.map(pa -> pa.descent(pat -> resolvePattern(pat, mCtx)));
    exit();
    enter(Where.FnBody);
    var body = clause.expr.map(x -> x.descent(enter(mCtx.get())));
    exit();
    return clause.update(pats, body);
  }

  public @NotNull WithPos<Pattern> resolvePattern(@NotNull WithPos<Pattern> pattern, MutableValue<Context> ctx) {
    var resolver = new PatternResolver(ctx.get(), this::addReference);
    var result = pattern.descent(resolver);
    ctx.set(resolver.context());
    return result;
  }

  private static Context bindAs(@NotNull LocalVar as, @NotNull Context ctx) { return ctx.bind(as); }

  public @NotNull Expr.Param bind(@NotNull Expr.Param param, @NotNull MutableValue<Context> ctx) {
    var p = param.descent(enter(ctx.get()));
    ctx.set(ctx.get().bind(param.ref()));
    return p;
  }

  public @NotNull ImmutableSeq<Expr.DoBind>
  bind(@NotNull ImmutableSeq<Expr.DoBind> binds, @NotNull MutableValue<Context> ctx) {
    return binds.map(bind -> {
      var b = bind.descent(enter(ctx.get()));
      ctx.set(ctx.get().bind(bind.var()));
      return b;
    });
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

// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableStack;
import kala.value.MutableValue;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.GeneralizedVar;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.concrete.visitor.EndoExpr;
import org.aya.concrete.visitor.EndoPattern;
import org.aya.core.def.CtorDef;
import org.aya.core.def.PrimDef;
import org.aya.generic.util.InternalException;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.ModulePath;
import org.aya.resolve.error.GeneralizedNotAvailableError;
import org.aya.resolve.error.PrimResolveError;
import org.aya.tyck.error.FieldError;
import org.aya.tyck.order.TyckOrder;
import org.aya.tyck.order.TyckUnit;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Resolves bindings.
 *
 * @param allowedGeneralizes will be filled with generalized vars if allowGeneralized,
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
  @NotNull MutableStack<Where> where,
  @Nullable Consumer<TyckUnit> parentAdd
) implements EndoExpr {

  public ExprResolver(@NotNull Context ctx, @NotNull Options options) {
    this(ctx, options, MutableLinkedHashMap.of(), MutableList.create(), MutableStack.create(), null);
  }

  public static final @NotNull Options RESTRICTIVE = new Options(false);
  public static final @NotNull Options LAX = new Options(true);

  @NotNull Expr.PartEl partial(@NotNull Context ctx, Expr.PartEl el) {
    return el.descent(enter(ctx));
  }

  public void enterHead(@NotNull Stmt.Accessibility accessibility) {
    where.push(new Where.Head(accessibility));
    reference.clear();
  }

  public void enterBody() {
    where.push(Where.Body);
    reference.clear();
  }

  public @NotNull ExprResolver enter(Context ctx) {
    return ctx == ctx() ? this : new ExprResolver(ctx, options, allowedGeneralizes, reference, where, parentAdd);
  }

  public @NotNull ExprResolver member(@NotNull TyckUnit decl) {
    return new ExprResolver(ctx, RESTRICTIVE, allowedGeneralizes, MutableList.of(new TyckOrder.Head(decl)), MutableStack.create(),
      this::addReference);
  }

  public @NotNull ExprResolver body() {
    return new ExprResolver(ctx, RESTRICTIVE, allowedGeneralizes, reference, MutableStack.create(), this::addReference);
  }

  private @Nullable Stmt.Accessibility accessibility() {
    return switch (where().peek()) {
      case Where.Body body -> null;
      case Where.Head head -> head.accessibility();
    };
  }

  @Override public @NotNull Expr pre(@NotNull Expr expr) {
    return switch (expr) {
      case Expr.RawProj(var pos, var tup, var id, var resolved, var coeLeft, var restr) -> {
        var resolvedIx = ctx.getMaybe(id);
        if (resolvedIx == null)
          ctx.reportAndThrow(new FieldError.UnknownField(id.sourcePos(), id.join()));
        yield resolvedIx.data() == resolved ? expr
          : new Expr.RawProj(pos, tup, id, resolvedIx.data(), coeLeft, restr);
      }
      case Expr.Hole hole -> {
        hole.accessibleLocal().set(ctx.collect(MutableList.create()).toImmutableSeq());
        yield hole;
      }
      default -> EndoExpr.super.pre(expr);
    };
  }

  /**
   * Special handling of terms with binding structure.
   * We need to invoke a resolver with a different context under the binders.
   */
  @Override public @NotNull Expr apply(@NotNull Expr expr) {
    return switch (expr) {
      case Expr.Do doExpr -> doExpr.update(apply(doExpr.bindName()), resolve(doExpr.binds(), MutableValue.create(ctx)));
      case Expr.Match match -> {
        var clauses = match.clauses().map(this::apply);
        yield match.update(match.discriminant().map(this), clauses);
      }
      case Expr.New neu -> neu.update(apply(neu.struct()), neu.fields().map(field -> {
        var fieldCtx = field.bindings().foldLeft(ctx, (c, x) -> c.bind(x.data(), x.sourcePos()));
        return field.descent(enter(fieldCtx));
      }));
      case Expr.Lambda lam -> {
        var mCtx = MutableValue.create(ctx);
        var param = resolve(lam.param(), mCtx);
        yield lam.update(param, enter(mCtx.get()).apply(lam.body()));
      }
      case Expr.Pi pi -> {
        var mCtx = MutableValue.create(ctx);
        var param = resolve(pi.param(), mCtx);
        yield pi.update(param, enter(mCtx.get()).apply(pi.last()));
      }
      case Expr.Sigma sigma -> {
        var mCtx = MutableValue.create(ctx);
        var params = sigma.params().map(param -> resolve(param, mCtx));
        yield sigma.update(params);
      }
      case Expr.Path path -> {
        var newCtx = path.params().foldLeft(ctx, (c, x) -> c.bind(x, x.definition()));
        yield path.descent(enter(newCtx));
      }
      case Expr.Array array -> array.update(array.arrayBlock().map(
        left -> {
          var mCtx = MutableValue.create(ctx);
          var binds = resolve(left.binds(), mCtx);
          var generator = enter(mCtx.get()).apply(left.generator());
          return left.update(generator, binds, apply(left.bindName()), apply(left.pureName()));
        },
        right -> right.descent(this)
      ));
      case Expr.Unresolved(var pos, var name) -> switch (ctx.get(name, accessibility()).data()) {
        case GeneralizedVar generalized -> {
          if (!allowedGeneralizes.containsKey(generalized)) {
            if (options.allowGeneralized) {
              // Ordered set semantics. Do not expect too many generalized vars.
              var owner = generalized.owner;
              assert owner != null : "Sainty check";
              allowedGeneralizes.put(generalized, owner.toExpr(false, generalized.toLocal()));
              addReference(owner);
            } else {
              ctx.reportAndThrow(new GeneralizedNotAvailableError(pos, generalized));
            }
          }
          yield new Expr.Ref(pos, allowedGeneralizes.get(generalized).ref());
        }
        case DefVar<?, ?> def -> {
          // RefExpr is referring to a serialized core which is already tycked.
          // Collecting tyck order for tycked terms is unnecessary, just skip.
          if (def.concrete == null) assert def.core != null;
          else if (def.concrete instanceof TyckUnit unit) addReference(unit);
          if (def.core instanceof PrimDef prim && PrimDef.ID.projSyntax(prim.id))
            ctx.reportAndThrow(new PrimResolveError.BadUsage(name.join(), pos));
          yield new Expr.Ref(pos, def);
        }
        case AnyVar var -> new Expr.Ref(pos, var);
      };
      case Expr.Let(var $, var letBind, var body) let -> {
        // resolve letBind

        var mCtx = MutableValue.create(ctx);
        // visit telescope
        var telescope = letBind.telescope().map(param -> resolve(param, mCtx));
        // for things that can refer the telescope (like result and definedAs)
        var resolver = enter(mCtx.get());
        // visit result
        var result = resolver.apply(letBind.result());
        // visit definedAs
        var definedAs = resolver.apply(letBind.definedAs());

        // end resolve letBind

        // resolve body
        var newBody = enter(ctx.bind(letBind.bindName(), letBind.bindName().definition()))
          .apply(body);

        yield let.update(
          letBind.update(telescope, result, definedAs),
          newBody
        );
      }
      default -> EndoExpr.super.apply(expr);
    };
  }

  private void addReference(@NotNull TyckUnit unit) {
    if (parentAdd != null) parentAdd.accept(unit);
    if (where.isEmpty()) throw new InternalException("where am I?");
    if (where.peek() instanceof Where.Head) {
      reference.append(new TyckOrder.Head(unit));
      reference.append(new TyckOrder.Body(unit));
    } else {
      reference.append(new TyckOrder.Body(unit));
    }
  }

  public @NotNull Pattern.Clause apply(@NotNull Pattern.Clause clause) {
    var mCtx = MutableValue.create(ctx());
    var pats = clause.patterns.map(pa -> pa.descent(pat -> resolve(pat, mCtx)));
    return clause.update(pats, clause.expr.map(enter(mCtx.get())));
  }

  public static @NotNull Pattern resolve(@NotNull Pattern pattern, @NotNull MutableValue<Context> ctx) {
    return new EndoPattern() {
      @Override public @NotNull Pattern post(@NotNull Pattern pattern) {
        return switch (pattern) {
          case Pattern.Bind bind -> {
            var maybe = ctx.get().iterate(c -> {
              var myMaybe = c.getUnqualifiedLocalMaybe(bind.bind().name(), null, bind.sourcePos());
              if (myMaybe == null) return null;
              if (myMaybe.data() instanceof DefVar<?, ?> def && (
                def.core instanceof CtorDef
                  || def.concrete instanceof TeleDecl.DataCtor
                  || def.core instanceof PrimDef
                  || def.concrete instanceof TeleDecl.PrimDecl
              )) return def;

              return null;
            });
            if (maybe != null) yield new Pattern.Ctor(bind, maybe);
            ctx.set(ctx.get().bind(bind.bind(), bind.sourcePos(), var -> false));
            yield bind;
          }
          case Pattern.QualifiedRef qref -> {
            var qid = qref.qualifiedID();
            assert qid.component() instanceof ModulePath.Qualified;
            var maybe = ctx.get().iterate(c -> {
              var myMaybe = c.getQualifiedLocalMaybe((ModulePath.Qualified) qid.component(), qid.name(), null, qref.sourcePos());
              if (myMaybe == null) return null;

              if (myMaybe.data() instanceof DefVar<?, ?> def && (
                def.core instanceof CtorDef
                  || def.concrete instanceof TeleDecl.DataCtor
                  || def.core instanceof PrimDef
                  || def.concrete instanceof TeleDecl.PrimDecl
              )) return def;

              return null;
            });
            if (maybe != null) yield new Pattern.Ctor(qref, maybe);
            yield EndoPattern.super.post(pattern);
          }
          case Pattern.As as -> {
            ctx.set(bindAs(as.as(), ctx.get(), as.sourcePos()));
            yield as;
          }
          default -> EndoPattern.super.post(pattern);
        };
      }
    }.apply(pattern);
  }

  private static Context bindAs(@NotNull LocalVar as, @NotNull Context ctx, @NotNull SourcePos sourcePos) {
    return ctx.bind(as, sourcePos);
  }

  public @NotNull Expr.Param resolve(@NotNull Expr.Param param, @NotNull MutableValue<Context> ctx) {
    var p = param.descent(enter(ctx.get()));
    ctx.set(ctx.get().bind(param.ref(), param.sourcePos()));
    return p;
  }

  public @NotNull ImmutableSeq<Expr.DoBind>
  resolve(@NotNull ImmutableSeq<Expr.DoBind> binds, @NotNull MutableValue<Context> ctx) {
    return binds.map(bind -> {
      var b = bind.descent(enter(ctx.get()));
      ctx.set(ctx.get().bind(bind.var(), bind.sourcePos()));
      return b;
    });
  }

  public sealed interface Where {
    record Head(@NotNull Stmt.Accessibility accessibility) implements Where {
    }

    record Body() implements Where {
    }

    Body Body = new Body();
  }

  public record Options(boolean allowGeneralized) {
  }
}

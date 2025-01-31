// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.value.MutableValue;
import org.aya.generic.stmt.TyckOrder;
import org.aya.generic.stmt.TyckUnit;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.ResolvingStmt;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.NameProblem;
import org.aya.resolve.visitor.ExprResolver.Where;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.stmt.Generalize;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.ref.GeneralizedVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.error.TyckOrderError;
import org.aya.util.error.Panic;
import org.aya.util.error.PosedUnaryOperator;
import org.jetbrains.annotations.NotNull;

/// Resolves expressions inside stmts, after [StmtPreResolver]
///
/// @author re-xyr, ice1000, kiva
/// @see StmtPreResolver
/// @see ExprResolver
public interface StmtResolver {
  static void resolveStmt(@NotNull ImmutableSeq<ResolvingStmt> stmt, @NotNull ResolveInfo info) {
    var todos = stmt.flatMap(s -> resolveStmt(s, info));
    class OvergrownGeneralizer extends OverGeneralizer {
      final MutableMap<GeneralizedVar, Expr.Param> dependencyGeneralizes = MutableLinkedHashMap.of();
      final ResolveStmt task;
      // MutableList<Generalize> deps = MutableList.create();
      public OvergrownGeneralizer(@NotNull ResolveStmt task) {
        super(info.thisModule());
        this.task = task;
      }
      @Override protected boolean contains(@NotNull GeneralizedVar var) {
        return dependencyGeneralizes.containsKey(var) || task.generalizes.containsKey(var);
      }
      @Override protected void introduceDependency(@NotNull GeneralizedVar var, Expr.@NotNull Param param) {
        var owner = var.owner;
        assert owner != null : "GeneralizedVar owner should not be null";
        dependencyGeneralizes.put(var, param);
        // deps.append(owner);
      }
    }

    todos.forEach(task -> {
      if (task.stmt instanceof Generalize gen) {
        var generalizer = new OvergrownGeneralizer(task);
        generalizer.currentPath.appendAll(gen.variables);
        // Check loops
        task.generalizes.forEach((depGen, _) -> {
          generalizer.currentPath.append(depGen);
          depGen.owner.dependencies.forEach(generalizer::introduceDependencies);
          generalizer.currentPath.removeLast();
        });
      }
    });
    todos.forEach(task -> {
      if (task.stmt instanceof TeleDecl decl) {
        var generalizer = new OvergrownGeneralizer(task);
        task.generalizes.forEach((gen, _) -> gen.owner.dependencies.forEach(generalizer::introduceDependencies));
        insertGeneralizedVars(decl, task.generalizes);
        insertGeneralizedVars(decl, generalizer.dependencyGeneralizes);
        // addReferences(info, new TyckOrder.Head(gen), generalizer.deps.view().map(TyckOrder.Head::new));
      }
    });
  }

  /// @param generalizes the directly referred generalized variables
  record ResolveStmt(Stmt stmt, MutableMap<GeneralizedVar, Expr.Param> generalizes) { }

  /// @return the "TO-DO" for the rest of the resolving, see [ResolveStmt]
  /// @apiNote Note that this function MUTATES the stmt if it's a Decl.
  static Option<ResolveStmt> resolveStmt(@NotNull ResolvingStmt stmt, @NotNull ResolveInfo info) {
    return switch (stmt) {
      case ResolvingStmt.ResolvingDecl decl -> resolveDecl(decl, info);
      case ResolvingStmt.ModStmt(var stmts) -> {
        resolveStmt(stmts, info);
        yield Option.none();
      }
      case ResolvingStmt.GenStmt(var variables, var context) -> {
        var resolver = new ExprResolver(context, true);
        resolver.enter(Where.Head);
        variables.descentInPlace(resolver, PosedUnaryOperator.identity());
        variables.dependencies = ImmutableMap.from(resolver.allowedGeneralizes().view());
        addReferences(info, new TyckOrder.Head(variables), resolver);
        yield Option.some(new ResolveStmt(variables, resolver.allowedGeneralizes()));
      }
    };
  }

  /// Resolve {@param predecl}, where `predecl.ctx()` is the context of the body of {@param predecl}
  ///
  /// @apiNote Note that this function MUTATES the decl
  private static Option<ResolveStmt>
  resolveDecl(@NotNull ResolvingStmt.ResolvingDecl predecl, @NotNull ResolveInfo info) {
    switch (predecl) {
      case ResolvingStmt.TopDecl(FnDecl decl, var ctx) -> {
        var where = decl.body instanceof FnBody.BlockBody ? Where.Head : Where.FnSimple;
        // Generalized works for simple bodies and signatures
        var resolver = resolveDeclSignature(info, new ExprResolver(ctx, true), decl, where);
        switch (decl.body) {
          case FnBody.BlockBody body -> {
            assert body.elims() == null;
            // insertGeneralizedVars(decl, resolver);
            resolveElim(resolver, body.inner());
            var clausesResolver = resolver.deriveRestrictive();
            clausesResolver.reference().append(new TyckOrder.Head(decl));
            decl.body = body.map(x -> clausesResolver.clause(decl.teleVars().toImmutableSeq(), x));
            addReferences(info, new TyckOrder.Body(decl), clausesResolver);
          }
          case FnBody.ExprBody(var expr) -> {
            var body = expr.descent(resolver);
            // insertGeneralizedVars(decl, resolver);
            decl.body = new FnBody.ExprBody(body);
            addReferences(info, new TyckOrder.Head(decl), resolver);
          }
        }
        if (resolver.allowedGeneralizes().isNotEmpty())
          return Option.some(new ResolveStmt(decl, resolver.allowedGeneralizes()));
      }
      case ResolvingStmt.TopDecl(DataDecl data, var ctx) -> {
        var resolver = resolveDeclSignature(info, new ExprResolver(ctx, true), data, Where.Head);
        // insertGeneralizedVars(data, resolver);
        resolveElim(resolver, data.body);
        data.body.forEach(con -> {
          var bodyResolver = resolver.deriveRestrictive();
          var mCtx = MutableValue.create(resolver.ctx());
          bodyResolver.reference().append(new TyckOrder.Head(data));
          bodyResolver.enter(Where.ConPattern);
          con.patterns = con.patterns.map(pat ->
            pat.descent(pattern ->
              bodyResolver.resolvePattern(pattern, data.teleVars().toImmutableSeq(), mCtx)));
          bodyResolver.exit();
          resolveMemberSignature(con, bodyResolver, mCtx);
          addReferences(info, new TyckOrder.Head(con), bodyResolver);
          // No body no body but you!
        });

        addReferences(info, new TyckOrder.Body(data), resolver.reference().view()
          .concat(data.body.clauses.map(TyckOrder.Body::new)));
        if (resolver.allowedGeneralizes().isNotEmpty())
          return Option.some(new ResolveStmt(data, resolver.allowedGeneralizes()));
      }
      case ResolvingStmt.TopDecl(ClassDecl decl, var ctx) -> {
        var resolver = new ExprResolver(ctx, false);
        resolver.enter(Where.Head);
        decl.members.forEach(field -> {
          var bodyResolver = resolver.member(decl, ExprResolver.Where.Head);
          var mCtx = MutableValue.create(resolver.ctx());
          resolveMemberSignature(field, bodyResolver, mCtx);
          addReferences(info, new TyckOrder.Head(field), bodyResolver.reference().view()
            .appended(new TyckOrder.Head(decl)));
          // TODO: body
          // bodyResolver.enter(Where.FnSimple);
          // field.body = field.body.map(bodyResolver.enter(mCtx.get()));
          // addReferences(info, new TyckOrder.Body(field), bodyResolver);
        });
        addReferences(info, new TyckOrder.Body(decl), resolver.reference().view()
          .concat(decl.members.map(TyckOrder.Head::new)));
      }
      case ResolvingStmt.TopDecl(PrimDecl decl, var ctx) -> {
        resolveDeclSignature(info, new ExprResolver(ctx, false), decl, Where.Head);
        addReferences(info, new TyckOrder.Body(decl), SeqView.empty());
      }
      case ResolvingStmt.TopDecl _ -> Panic.unreachable();
      // handled in DataDecl and ClassDecl
      case ResolvingStmt.MiscDecl _ -> Panic.unreachable();
    }
    return Option.none();
  }
  private static void
  resolveMemberSignature(TeleDecl con, ExprResolver bodyResolver, MutableValue<@NotNull Context> mCtx) {
    bodyResolver.enter(Where.Head);
    con.telescope = con.telescope.map(param -> bodyResolver.bind(param, mCtx));
    // If changed to method reference, `bodyResolver.enter(mCtx.get())` will be evaluated eagerly
    //  so please don't
    con.modifyResult((pos, t) -> bodyResolver.enter(mCtx.get()).apply(pos, t));
    bodyResolver.exit();
  }

  private static void addReferences(@NotNull ResolveInfo info, TyckOrder decl, SeqView<TyckOrder> refs) {
    // check self-reference
    if (decl instanceof TyckOrder.Head head && refs.contains(head)) {
      info.opSet().fail(new TyckOrderError.SelfReference(head.unit()));
      throw new Context.ResolvingInterruptedException();
    }

    info.depGraph().sucMut(decl).appendAll(refs
      .filter(unit -> TyckUnit.needTyck(unit, info.modulePath())));
  }

  /// @param decl is unmodified
  private static void addReferences(@NotNull ResolveInfo info, TyckOrder decl, ExprResolver resolver) {
    addReferences(info, decl, resolver.reference().view());
  }

  private static @NotNull ExprResolver
  resolveDeclSignature(
    @NotNull ResolveInfo info, @NotNull ExprResolver resolver,
    @NotNull TeleDecl stmt, @NotNull Where where
  ) {
    resolver.enter(where);
    var mCtx = MutableValue.create(resolver.ctx());
    var telescope = stmt.telescope.map(param -> resolver.bind(param, mCtx));
    var newResolver = resolver.enter(mCtx.get());
    stmt.modifyResult(newResolver);
    stmt.telescope = telescope;
    addReferences(info, new TyckOrder.Head(stmt), resolver);
    resolver.resetRefs();
    return newResolver;
  }

  private static void insertGeneralizedVars(@NotNull TeleDecl decl, MutableMap<GeneralizedVar, Expr.Param> generalizes) {
    decl.telescope = decl.telescope.prependedAll(generalizes.valuesView());
  }

  private static <Cls> void resolveElim(@NotNull ExprResolver resolver, @NotNull MatchBody<Cls> body) {
    if (body.elims() != null) {
      // TODO: panic or just return?
      return;
    }

    var resolved = body.rawElims.map(elim -> {
      var result = resolver.resolve(new QualifiedID(elim.sourcePos(), elim.data()));
      if (!(result instanceof LocalVar localVar)) {
        return resolver.ctx().reportAndThrow(new NameProblem.UnqualifiedNameNotFoundError(resolver.ctx(),
          elim.data(), elim.sourcePos()));
      }
      // result is LocalVar -> result in telescope
      return localVar;
    });

    body.resolve(resolved);
  }
}

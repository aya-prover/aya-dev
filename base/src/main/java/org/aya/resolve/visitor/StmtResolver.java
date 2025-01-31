// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.value.MutableValue;
import org.aya.generic.stmt.TyckOrder;
import org.aya.generic.stmt.TyckUnit;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.ResolvingStmt;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.NameProblem;
import org.aya.resolve.visitor.ExprResolver.Where;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.concrete.stmt.decl.*;
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
    stmt.forEach(s -> resolveStmt(s, info));
  }

  /// @apiNote Note that this function MUTATES the stmt if it's a Decl.
  static void resolveStmt(@NotNull ResolvingStmt stmt, @NotNull ResolveInfo info) {
    switch (stmt) {
      case ResolvingStmt.ResolvingDecl decl -> resolveDecl(decl, info);
      case ResolvingStmt.ModStmt(var stmts) -> resolveStmt(stmts, info);
      case ResolvingStmt.GenStmt(var variables) -> {
        var resolver = new ExprResolver(info.thisModule(), true);
        resolver.enter(Where.Head);
        variables.descentInPlace(resolver, PosedUnaryOperator.identity());
        variables.dependencies = ImmutableMap.from(resolver.allowedGeneralizes().view());
        addReferences(info, new TyckOrder.Head(variables), resolver);
      }
    }
  }

  /// Resolve {@param predecl}, where `predecl.ctx()` is the context of the body of {@param predecl}
  ///
  /// @apiNote Note that this function MUTATES the decl
  private static void resolveDecl(@NotNull ResolvingStmt.ResolvingDecl predecl, @NotNull ResolveInfo info) {
    switch (predecl) {
      case ResolvingStmt.TopDecl(FnDecl decl, var ctx) -> {
        var where = decl.body instanceof FnBody.BlockBody ? Where.Head : Where.FnSimple;
        // Generalized works for simple bodies and signatures
        var resolver = resolveDeclSignature(info, new ExprResolver(ctx, true), decl, where);
        switch (decl.body) {
          case FnBody.BlockBody body -> {
            assert body.elims() == null;
            // introducing generalized variable is not allowed in clauses, hence we insert them before body resolving
            insertGeneralizedVars(decl, resolver);
            resolveElim(resolver, body.inner());
            var clausesResolver = resolver.deriveRestrictive();
            clausesResolver.reference().append(new TyckOrder.Head(decl));
            decl.body = body.map(x -> clausesResolver.clause(decl.teleVars().toImmutableSeq(), x));
            addReferences(info, new TyckOrder.Body(decl), clausesResolver);
          }
          case FnBody.ExprBody(var expr) -> {
            var body = expr.descent(resolver);
            insertGeneralizedVars(decl, resolver);
            decl.body = new FnBody.ExprBody(body);
            addReferences(info, new TyckOrder.Head(decl), resolver);
          }
        }
      }
      case ResolvingStmt.TopDecl(DataDecl data, var ctx) -> {
        var resolver = resolveDeclSignature(info, new ExprResolver(ctx, true), data, Where.Head);
        insertGeneralizedVars(data, resolver);
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

  private static void insertGeneralizedVars(@NotNull TeleDecl decl, @NotNull ExprResolver resolver) {
    decl.telescope = decl.telescope.prependedAll(resolver.allowedGeneralizes().valuesView());
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

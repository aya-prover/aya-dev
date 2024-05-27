// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.value.MutableValue;
import org.aya.generic.stmt.TyckOrder;
import org.aya.generic.stmt.TyckUnit;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.ResolvingStmt;
import org.aya.resolve.context.Context;
import org.aya.resolve.visitor.ExprResolver.Where;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.util.error.Panic;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves expressions inside stmts, after {@link StmtPreResolver}
 *
 * @author re-xyr, ice1000, kiva
 * @see StmtPreResolver
 * @see ExprResolver
 */
public interface StmtResolver {
  static void resolveStmt(@NotNull ImmutableSeq<ResolvingStmt> stmt, @NotNull ResolveInfo info) {
    stmt.forEach(s -> resolveStmt(s, info));
  }

  /** @apiNote Note that this function MUTATES the stmt if it's a Decl. */
  static void resolveStmt(@NotNull ResolvingStmt stmt, @NotNull ResolveInfo info) {
    switch (stmt) {
      case ResolvingStmt.ResolvingDecl decl -> resolveDecl(decl, info);
      case ResolvingStmt.ModStmt(var stmts) -> resolveStmt(stmts, info);
      case ResolvingStmt.GenStmt(var variables) -> {
        var resolver = new ExprResolver(info.thisModule(), ExprResolver.RESTRICTIVE);
        resolver.enter(Where.Head);
        variables.descentInPlace(resolver, (_, p) -> p);
        addReferences(info, new TyckOrder.Head(variables), resolver);
      }
    }
  }

  /**
   * Resolve {@param predecl}, where {@code predecl.ctx()} is the context of the body of {@param predecl}
   *
   * @apiNote Note that this function MUTATES the decl
   */
  private static void resolveDecl(@NotNull ResolvingStmt.ResolvingDecl predecl, @NotNull ResolveInfo info) {
    switch (predecl) {
      case ResolvingStmt.TopDecl(FnDecl decl, var ctx) -> {
        var where = decl.body instanceof FnBody.BlockBody ? Where.Head : Where.FnSimple;
        // Generalized works for simple bodies and signatures
        var resolver = resolveDeclSignature(ExprResolver.LAX, info, ctx, decl, where);
        switch (decl.body) {
          case FnBody.BlockBody(var cls, var elims) -> {
            // introducing generalized variable is not allowed in clauses, hence we insert them before body resolving
            insertGeneralizedVars(decl, resolver);
            var clausesResolver = resolver.deriveRestrictive();
            clausesResolver.reference().append(new TyckOrder.Head(decl));
            decl.body = new FnBody.BlockBody(cls.map(clausesResolver::clause), elims);
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
        var resolver = resolveDeclSignature(ExprResolver.LAX, info, ctx, data, Where.Head);
        insertGeneralizedVars(data, resolver);
        data.body.forEach(con -> {
          var bodyResolver = resolver.deriveRestrictive();
          var mCtx = MutableValue.create(resolver.ctx());
          bodyResolver.reference().append(new TyckOrder.Head(data));
          bodyResolver.enter(Where.ConPattern);
          con.patterns = con.patterns.map(pat -> pat.descent(pattern -> bodyResolver.resolvePattern(pattern, mCtx)));
          bodyResolver.exit();
          resolveMemberSignature(con, bodyResolver, mCtx);
          addReferences(info, new TyckOrder.Head(con), bodyResolver);
          // No body no body but you!
        });
        addReferences(info, new TyckOrder.Body(data), resolver.reference().view()
          .concat(data.body.map(TyckOrder.Body::new)));
      }
      case ResolvingStmt.TopDecl(PrimDecl decl, var ctx) -> {
        resolveDeclSignature(ExprResolver.RESTRICTIVE, info, ctx, decl, Where.Head);
        addReferences(info, new TyckOrder.Body(decl), SeqView.empty());
      }
      case ResolvingStmt.TopDecl _ -> Panic.unreachable();
      // case ClassDecl decl -> {
      //   assert decl.ctx != null;
      //   var resolver = new ExprResolver(decl.ctx, ExprResolver.RESTRICTIVE);
      //   resolver.enterHead();
      //   decl.members.forEach(field -> {
      //     var bodyResolver = resolver.member(decl, ExprResolver.Where.Head);
      //     var mCtx = MutableValue.create(resolver.ctx());
      //     resolveMemberSignature(field, bodyResolver, mCtx);
      //     addReferences(info, new TyckOrder.Head(field), bodyResolver.reference().view()
      //       .appended(new TyckOrder.Head(decl)));
      //     bodyResolver.enterBody();
      //     field.body = field.body.map(bodyResolver.enter(mCtx.get()));
      //     addReferences(info, new TyckOrder.Body(field), bodyResolver);
      //   });
      //   addReferences(info, new TyckOrder.Head(decl), resolver.reference().view()
      //     .concat(decl.members.map(TyckOrder.Head::new)));
      // }
      // handled in DataDecl and ClassDecl
      case ResolvingStmt.MiscDecl _ -> Panic.unreachable();
      // case TeleDecl.ClassMember field -> {}
    }
  }
  private static void
  resolveMemberSignature(Decl con, ExprResolver bodyResolver, MutableValue<@NotNull Context> mCtx) {
    bodyResolver.enter(Where.Head);
    con.telescope = con.telescope.map(param -> bodyResolver.bind(param, mCtx));
    // If changed to method reference, `bodyResolver.enter(mCtx.get())` will be evaluated eagerly
    //  so please don't
    con.modifyResult((pos, t) -> bodyResolver.enter(mCtx.get()).apply(pos, t));
    bodyResolver.exit();
  }

  private static void addReferences(@NotNull ResolveInfo info, TyckOrder decl, SeqView<TyckOrder> refs) {
    info.depGraph().sucMut(decl).appendAll(refs
      .filter(unit -> TyckUnit.needTyck(unit, info.thisModule().modulePath())));
  }

  /** @param decl is unmodified */
  private static void addReferences(@NotNull ResolveInfo info, TyckOrder decl, ExprResolver resolver) {
    addReferences(info, decl, resolver.reference().view());
  }

  private static @NotNull ExprResolver
  resolveDeclSignature(
    @NotNull ExprResolver.Options options, @NotNull ResolveInfo info,
    @NotNull Context ctx, Decl stmt, Where where
  ) {
    var resolver = new ExprResolver(ctx, options);
    resolver.enter(where);
    var mCtx = MutableValue.create(ctx);
    var telescope = stmt.telescope.map(param -> resolver.bind(param, mCtx));
    var newResolver = resolver.enter(mCtx.get());
    stmt.modifyResult(newResolver);
    stmt.telescope = telescope;
    addReferences(info, new TyckOrder.Head(stmt), resolver);
    resolver.resetRefs();
    return newResolver;
  }

  private static void insertGeneralizedVars(
    @NotNull Decl decl, @NotNull ExprResolver resolver
  ) {
    decl.telescope = decl.telescope.prependedAll(resolver.allowedGeneralizes().valuesView());
  }

  @Contract("_, _ -> fail")
  static Context.ResolvingInterruptedException resolvingInterrupt(Reporter reporter, Problem problem) {
    reporter.report(problem);
    throw new Context.ResolvingInterruptedException();
  }
}

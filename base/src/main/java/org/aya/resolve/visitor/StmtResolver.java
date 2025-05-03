// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
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
import org.aya.resolve.error.NameProblem;
import org.aya.resolve.visitor.ExprResolver.Where;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.error.TyckOrderError;
import org.aya.util.Panic;
import org.aya.util.reporter.LocalReporter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Resolves expressions inside stmts, after {@link StmtPreResolver}
 *
 * @author re-xyr, ice1000, kiva
 * @see StmtPreResolver
 * @see ExprResolver
 */
public class StmtResolver {
  private final @NotNull ResolveInfo info;
  private final @NotNull LocalReporter reporter;

  public StmtResolver(@NotNull ResolveInfo info, @NotNull LocalReporter reporter) {
    this.info = info;
    this.reporter = reporter;
  }

  public static void resolveStmt(
    @NotNull ImmutableSeq<ResolvingStmt> stmt,
    @NotNull ResolveInfo info, @NotNull LocalReporter reporter
  ) {
    var resolver = new StmtResolver(info, reporter);
    stmt.forEach(resolver::resolveStmt);
  }

  /** @apiNote Note that this function MUTATES the stmt if it's a Decl. */
  private void resolveStmt(@NotNull ResolvingStmt stmt) {
    switch (stmt) {
      case ResolvingStmt.ResolvingDecl decl -> resolveDecl(decl);
      case ResolvingStmt.ModStmt(var stmts) -> stmts.forEach(this::resolveStmt);
      case ResolvingStmt.GenStmt(var variables, var context) -> {
        var resolver = new ExprResolver(context, reporter, false);
        resolver.enter(Where.Head);
        variables.descentInPlace(resolver, (_, p) -> p);
        addReferences(new TyckOrder.Head(variables), resolver);
      }
    }
  }

  /**
   * Resolve {@param predecl}, where {@code predecl.ctx()} is the context of the body of {@param predecl}
   *
   * @apiNote Note that this function MUTATES the decl
   */
  private void resolveDecl(@NotNull ResolvingStmt.ResolvingDecl predecl) {
    switch (predecl) {
      case ResolvingStmt.TopDecl(FnDecl decl, var ctx) -> {
        var reporter = StmtPreResolver.suppress(this.reporter, decl);
        var where = decl.body instanceof FnBody.BlockBody ? Where.Head : Where.FnSimple;
        // Generalized works for simple bodies and signatures
        var resolver = resolveDeclSignature(new ExprResolver(ctx, reporter, true), decl, where);
        switch (decl.body) {
          case FnBody.BlockBody body -> {
            assert body.elims() == null;
            // introducing generalized variable is not allowed in clauses, hence we insert them before body resolving
            insertGeneralizedVars(decl, resolver);
            resolveElim(resolver, body.inner());
            var clausesResolver = resolver.deriveRestrictive();
            clausesResolver.reference().append(new TyckOrder.Head(decl));
            decl.body = body.map(x -> clausesResolver.clause(decl.teleVars().toSeq(), x, reporter));
            addReferences(new TyckOrder.Body(decl), clausesResolver);
          }
          case FnBody.ExprBody(var expr) -> {
            var body = expr.descent(resolver);
            insertGeneralizedVars(decl, resolver);
            decl.body = new FnBody.ExprBody(body);
            addReferences(new TyckOrder.Head(decl), resolver);
          }
        }
      }
      case ResolvingStmt.TopDecl(DataDecl data, var ctx) -> {
        var resolver = resolveDeclSignature(new ExprResolver(ctx, reporter, true), data, Where.Head);
        insertGeneralizedVars(data, resolver);
        resolveElim(resolver, data.body);
        data.body.forEach(con -> {
          var innerReporter = reporter;
          var bodyResolver = resolver.deriveRestrictive();
          var mCtx = MutableValue.create(resolver.ctx());
          bodyResolver.reference().append(new TyckOrder.Head(data));
          bodyResolver.enter(Where.ConPattern);
          con.patterns = con.patterns.map(pat ->
            pat.descent(pattern ->
              bodyResolver.resolvePattern(pattern, data.teleVars().toSeq(), mCtx, innerReporter)));
          bodyResolver.exit();
          resolveMemberSignature(con, bodyResolver, mCtx);
          addReferences(new TyckOrder.Head(con), bodyResolver);
          // No body no body but you!
        });

        addReferences(new TyckOrder.Body(data), resolver.reference().view()
          .concat(data.body.clauses.map(TyckOrder.Body::new)));
      }
      case ResolvingStmt.TopDecl(ClassDecl decl, var ctx) -> {
        var resolver = new ExprResolver(ctx, reporter, false);
        resolver.enter(Where.Head);
        decl.members.forEach(field -> {
          var bodyResolver = resolver.member(decl, ExprResolver.Where.Head);
          var mCtx = MutableValue.create(resolver.ctx());
          resolveMemberSignature(field, bodyResolver, mCtx);
          addReferences(new TyckOrder.Head(field), bodyResolver.reference().view()
            .appended(new TyckOrder.Head(decl)));
          // TODO: body
          // bodyResolver.enter(Where.FnSimple);
          // field.body = field.body.map(bodyResolver.enter(mCtx.get()));
          // addReferences(info, new TyckOrder.Body(field), bodyResolver);
        });
        addReferences(new TyckOrder.Body(decl), resolver.reference().view()
          .concat(decl.members.map(TyckOrder.Head::new)));
      }
      case ResolvingStmt.TopDecl(PrimDecl decl, var ctx) -> {
        resolveDeclSignature(new ExprResolver(ctx, reporter, false), decl, Where.Head);
        addReferences(new TyckOrder.Body(decl), SeqView.empty());
      }
      case ResolvingStmt.TopDecl _ -> Panic.unreachable();
      // handled in DataDecl and ClassDecl
      case ResolvingStmt.MiscDecl _ -> Panic.unreachable();
    }
  }
  private void
  resolveMemberSignature(TeleDecl con, ExprResolver bodyResolver, MutableValue<@NotNull Context> mCtx) {
    bodyResolver.enter(Where.Head);
    con.telescope = con.telescope.map(param -> bodyResolver.bind(param, mCtx));
    // If changed to method reference, `bodyResolver.enter(mCtx.get())` will be evaluated eagerly
    //  so please don't
    con.modifyResult((pos, t) -> bodyResolver.enter(mCtx.get()).apply(pos, t));
    bodyResolver.exit();
  }

  private void addReferences(TyckOrder decl, SeqView<TyckOrder> refs) {
    // check self-reference
    if (decl instanceof TyckOrder.Head head && refs.contains(head)) {
      info.opSet().fail(new TyckOrderError.SelfReference(head.unit()));
      return;
    }

    info.depGraph().sucMut(decl).appendAll(refs
      .filter(unit -> TyckUnit.needTyck(unit, info.modulePath())));
  }

  /** @param decl is unmodified */
  private void addReferences(TyckOrder decl, ExprResolver resolver) {
    addReferences(decl, resolver.reference().view());
  }

  private @NotNull ExprResolver
  resolveDeclSignature(
    @NotNull ExprResolver resolver,
    @NotNull TeleDecl stmt, @NotNull Where where
  ) {
    resolver.enter(where);
    var mCtx = MutableValue.create(resolver.ctx());
    var telescope = stmt.telescope.map(param -> resolver.bind(param, mCtx));
    var newResolver = resolver.enter(mCtx.get());
    stmt.modifyResult(newResolver);
    stmt.telescope = telescope;
    addReferences(new TyckOrder.Head(stmt), resolver);
    resolver.resetRefs();
    return newResolver;
  }

  private void insertGeneralizedVars(@NotNull TeleDecl decl, @NotNull ExprResolver resolver) {
    decl.telescope = decl.telescope.prependedAll(resolver.allowedGeneralizes().valuesView());
  }

  private <Cls> void resolveElim(@NotNull ExprResolver resolver, @NotNull MatchBody<Cls> body) {
    assert body.elims() == null;

    var resolved = body.rawElims.map(elim -> {
      var result = resolver.resolve(new QualifiedID(elim.sourcePos(), elim.data()));
      if (result == null) return null; // preventing duplicated reporting
      if (!(result instanceof LocalVar localVar)) {
        reporter.report(new NameProblem.UnqualifiedNameNotFoundError(resolver.ctx(),
          elim.data(), elim.sourcePos()));
        return null;
      }
      // result is LocalVar -> result in telescope
      return localVar;
    });

    if (resolved.anyMatch(Objects::isNull)) return;
    body.resolve(resolved);
  }
}

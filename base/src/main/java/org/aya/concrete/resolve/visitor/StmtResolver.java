// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.visitor;

import kala.collection.SeqLike;
import kala.value.Ref;
import org.aya.api.ref.DefVar;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.desugar.error.OperatorProblem;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.error.UnknownOperatorError;
import org.aya.concrete.stmt.*;
import org.aya.util.binop.OpDecl;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves expressions inside stmts, after {@link StmtShallowResolver}
 *
 * @author re-xyr, ice1000, kiva
 * @see StmtShallowResolver
 * @see ExprResolver
 */
public interface StmtResolver {
  static void resolveStmt(SeqLike<@NotNull Stmt> stmt, @NotNull ResolveInfo info) {
    stmt.forEach(s -> resolveStmt(s, info));
  }

  /** @apiNote Note that this function MUTATES the stmt if it's a Decl. */
  static void resolveStmt(@NotNull Stmt stmt, @NotNull ResolveInfo info) {
    switch (stmt) {
      case Command.Module mod -> resolveStmt(mod.contents(), info);
      case Decl.DataDecl decl -> {
        var signatureResolver = new ExprResolver(ExprResolver.LAX);
        var local = signatureResolver.resolveParams(decl.telescope, decl.ctx);
        decl.telescope = local._1.prependedAll(signatureResolver.allowedGeneralizes().valuesView());
        decl.result = decl.result.accept(signatureResolver, local._2);
        var bodyResolver = new ExprResolver(ExprResolver.RESTRICTIVE, signatureResolver);
        for (var ctor : decl.body) {
          var localCtxWithPat = new Ref<>(local._2);
          ctor.patterns = ctor.patterns.map(pattern -> PatResolver.INSTANCE.subpatterns(localCtxWithPat, pattern));
          var ctorLocal = bodyResolver.resolveParams(ctor.telescope, localCtxWithPat.value);
          ctor.telescope = ctorLocal._1;
          ctor.clauses = ctor.clauses.map(clause -> PatResolver.INSTANCE.matchy(clause, ctorLocal._2, bodyResolver));
        }
        info.declGraph().suc(decl).appendAll(signatureResolver.reference());
      }
      case Decl.FnDecl decl -> {
        var signatureResolver = new ExprResolver(ExprResolver.LAX);
        var local = signatureResolver.resolveParams(decl.telescope, decl.ctx);
        decl.telescope = local._1.prependedAll(signatureResolver.allowedGeneralizes().valuesView());
        decl.result = decl.result.accept(signatureResolver, local._2);
        var bodyResolver = new ExprResolver(ExprResolver.RESTRICTIVE, signatureResolver);
        decl.body = decl.body.map(
          expr -> expr.accept(bodyResolver, local._2),
          pats -> pats.map(clause -> PatResolver.INSTANCE.matchy(clause, local._2, bodyResolver)));
        info.declGraph().suc(decl).appendAll(signatureResolver.reference());
      }
      case Decl.StructDecl decl -> {
        var signatureResolver = new ExprResolver(ExprResolver.LAX);
        var local = signatureResolver.resolveParams(decl.telescope, decl.ctx);
        decl.telescope = local._1.prependedAll(signatureResolver.allowedGeneralizes().valuesView());
        decl.result = decl.result.accept(signatureResolver, local._2);
        var bodyResolver = new ExprResolver(ExprResolver.RESTRICTIVE, signatureResolver);
        decl.fields.forEach(field -> {
          var fieldLocal = bodyResolver.resolveParams(field.telescope, local._2);
          field.telescope = fieldLocal._1;
          field.result = field.result.accept(bodyResolver, fieldLocal._2);
          field.body = field.body.map(e -> e.accept(bodyResolver, fieldLocal._2));
          field.clauses = field.clauses.map(clause -> PatResolver.INSTANCE.matchy(clause, fieldLocal._2, bodyResolver));
        });
        info.declGraph().suc(decl).appendAll(signatureResolver.reference());
      }
      case Decl.PrimDecl decl -> {
        var resolver = new ExprResolver(ExprResolver.RESTRICTIVE);
        var local = resolver.resolveParams(decl.telescope, decl.ctx);
        decl.telescope = local._1;
        if (decl.result != null) decl.result = decl.result.accept(resolver, local._2);
        info.declGraph().suc(decl).appendAll(resolver.reference());
      }
      case Sample sample -> {
        var delegate = sample.delegate();
        var delegateInfo = new ResolveInfo(info.opSet());
        resolveStmt(delegate, delegateInfo);
        // little hacky: transfer dependencies from `delegate` to `sample`
        info.sampleGraph().suc(sample).appendAll(delegateInfo.declGraph().suc(delegate));
      }
      case Remark remark -> info.sampleGraph().suc(remark).appendAll(remark.doResolve(info));
      case Command cmd -> {}
      case Generalize.Levels levels -> {}
      case Generalize.Variables variables -> {
        var resolver = new ExprResolver(ExprResolver.RESTRICTIVE);
        variables.type = variables.type.accept(resolver, variables.ctx);
        info.declGraph().suc(variables).appendAll(resolver.reference());
      }
    }
  }

  static void visitBind(@NotNull DefVar<?, ?> selfDef, @NotNull OpDecl self, @NotNull BindBlock bind, ResolveInfo info) {
    if (bind == BindBlock.EMPTY) return;
    var ctx = bind.context().value;
    assert ctx != null : "no shallow resolver?";
    var opSet = info.opSet();
    if (opSet.isOperand(self)) {
      opSet.reporter.report(new OperatorProblem.NotOperator(selfDef.concrete.sourcePos(), selfDef.name()));
      throw new Context.ResolvingInterruptedException();
    }
    bind.resolvedLoosers().value = bind.loosers().map(looser -> bind(self, opSet, ctx, OpDecl.BindPred.Looser, looser));
    bind.resolvedTighters().value = bind.tighters().map(tighter -> bind(self, opSet, ctx, OpDecl.BindPred.Tighter, tighter));
  }

  private static @NotNull DefVar<?, ?> bind(
    @NotNull OpDecl self, @NotNull AyaBinOpSet opSet, @NotNull Context ctx,
    @NotNull OpDecl.BindPred pred, @NotNull QualifiedID id
  ) throws Context.ResolvingInterruptedException {
    if (ctx.get(id) instanceof DefVar<?, ?> defVar && defVar.concrete instanceof OpDecl op) {
      opSet.bind(self, pred, op, id.sourcePos());
      return defVar;
    } else {
      opSet.reporter.report(new UnknownOperatorError(id.sourcePos(), id.join()));
      throw new Context.ResolvingInterruptedException();
    }
  }

  static void resolveBind(SeqLike<@NotNull Stmt> contents, @NotNull ResolveInfo info) {
    contents.forEach(s -> resolveBind(s, info));
  }

  static void resolveBind(@NotNull Stmt stmt, @NotNull ResolveInfo info) {
    switch (stmt) {
      case Command.Module mod -> resolveBind(mod.contents(), info);
      case Decl.DataDecl decl -> {
        decl.body.forEach(ctor -> visitBind(ctor.ref, ctor, ctor.bindBlock, info));
        visitBind(decl.ref, decl, decl.bindBlock, info);
      }
      case Decl.StructDecl decl -> {
        decl.fields.forEach(field -> visitBind(field.ref, field, field.bindBlock, info));
        visitBind(decl.ref, decl, decl.bindBlock, info);
      }
      case Decl.FnDecl decl -> visitBind(decl.ref, decl, decl.bindBlock, info);
      case Sample sample -> resolveBind(sample.delegate(), info);
      case Remark remark -> {}
      case Command cmd -> {}
      case Decl.PrimDecl decl -> {}
      case Generalize.Levels levels -> {}
      case Generalize.Variables variables -> {}
    }
  }
}

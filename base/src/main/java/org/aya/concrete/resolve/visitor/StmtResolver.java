// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.visitor;

import kala.collection.mutable.DynamicSeq;
import kala.value.Ref;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Sample;
import org.aya.concrete.stmt.Stmt;
import org.aya.util.MutableGraph;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves expressions inside stmts, after {@link StmtShallowResolver}
 *
 * @author re-xyr, ice1000, kiva
 * @see StmtShallowResolver
 * @see ExprResolver
 */
public interface StmtResolver {
  /** @apiNote Note that this function MUTATES the stmt if it's a Decl. */
  static void visit(@NotNull Stmt stmt, @NotNull ResolveInfo info) {
    switch (stmt) {
      default -> {
        // do nothing
      }
      case Command.Module mod -> mod.contents().forEach(s -> visit(s, info));
      case Decl.DataDecl decl -> {
        var signatureResolver = new ExprResolver(true, DynamicSeq.create(), DynamicSeq.create());
        var local = signatureResolver.resolveParams(decl.telescope, decl.ctx);
        decl.telescope = local._1;
        decl.result = decl.result.accept(signatureResolver, local._2);
        var bodyResolver = new ExprResolver(false, signatureResolver);
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
        var signatureResolver = new ExprResolver(true, DynamicSeq.create(), DynamicSeq.create());
        var local = signatureResolver.resolveParams(decl.telescope, decl.ctx);
        decl.telescope = local._1;
        decl.result = decl.result.accept(signatureResolver, local._2);
        var bodyResolver = new ExprResolver(false, signatureResolver);
        decl.body = decl.body.map(
          expr -> expr.accept(bodyResolver, local._2),
          pats -> pats.map(clause -> PatResolver.INSTANCE.matchy(clause, local._2, bodyResolver)));
        info.declGraph().suc(decl).appendAll(signatureResolver.reference());
      }
      case Decl.StructDecl decl -> {
        var signatureResolver = new ExprResolver(true, DynamicSeq.create(), DynamicSeq.create());
        var local = signatureResolver.resolveParams(decl.telescope, decl.ctx);
        decl.telescope = local._1;
        decl.result = decl.result.accept(signatureResolver, local._2);
        var bodyResolver = new ExprResolver(false, signatureResolver);
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
        var resolver = new ExprResolver(false, DynamicSeq.create(), DynamicSeq.create());
        var local = resolver.resolveParams(decl.telescope, decl.ctx);
        decl.telescope = local._1;
        if (decl.result != null) decl.result = decl.result.accept(resolver, local._2);
        info.declGraph().suc(decl).appendAll(resolver.reference());
      }
      case Sample sample -> {
        var delegate = sample.delegate();
        var delegateInfo = new ResolveInfo(info.opSet(), MutableGraph.create(), MutableGraph.create());
        visit(delegate, delegateInfo);
        // little hacky: transfer dependencies from `delegate` to `sample`
        info.sampleGraph().suc(sample).appendAll(delegateInfo.declGraph().suc(delegate));
      }
      case Remark remark -> info.sampleGraph().suc(remark).appendAll(remark.doResolve(info));
    }
  }
}

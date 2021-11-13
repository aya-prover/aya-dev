// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.visitor;

import kala.collection.mutable.DynamicSeq;
import kala.tuple.Unit;
import kala.value.Ref;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.stmt.*;
import org.aya.util.MutableGraph;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves expressions inside stmts, after {@link StmtShallowResolver}
 *
 * @author re-xyr, ice1000, kiva
 * @see StmtShallowResolver
 * @see ExprResolver
 */
public final class StmtResolver implements Stmt.Visitor<ResolveInfo, Unit> {
  public static final @NotNull StmtResolver INSTANCE = new StmtResolver();

  private StmtResolver() {
  }

  @Override public Unit visitModule(Command.@NotNull Module mod, ResolveInfo info) {
    visitAll(mod.contents(), info);
    return Unit.unit();
  }

  @Override public Unit visitImport(Command.@NotNull Import cmd, ResolveInfo info) {
    return Unit.unit();
  }

  @Override public Unit visitOpen(Command.@NotNull Open cmd, ResolveInfo info) {
    return Unit.unit();
  }

  @Override public Unit visitCtor(@NotNull Decl.DataCtor ctor, ResolveInfo info) {
    throw new UnsupportedOperationException();
  }

  @Override public Unit visitField(@NotNull Decl.StructField field, ResolveInfo info) {
    throw new UnsupportedOperationException();
  }

  /** @apiNote Note that this function MUTATES the decl. */
  @Override public Unit visitData(Decl.@NotNull DataDecl decl, ResolveInfo info) {
    var reference = DynamicSeq.<Stmt>create();
    var signatureResolver = new ExprResolver(true, DynamicSeq.create(), reference);
    var local = signatureResolver.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    decl.result = decl.result.accept(signatureResolver, local._2);
    var bodyResolver = new ExprResolver(false, signatureResolver.allowedLevels(), reference);
    for (var ctor : decl.body) {
      var localCtxWithPat = new Ref<>(local._2);
      ctor.patterns = ctor.patterns.map(pattern -> PatResolver.INSTANCE.subpatterns(localCtxWithPat, pattern));
      var ctorLocal = bodyResolver.resolveParams(ctor.telescope, localCtxWithPat.value);
      ctor.telescope = ctorLocal._1;
      ctor.clauses = ctor.clauses.map(clause -> PatResolver.INSTANCE.matchy(clause, ctorLocal._2, bodyResolver));
    }
    info.declGraph().suc(decl).appendAll(reference);
    return Unit.unit();
  }

  @Override public Unit visitStruct(Decl.@NotNull StructDecl decl, ResolveInfo info) {
    var reference = DynamicSeq.<Stmt>create();
    var signatureResolver = new ExprResolver(true, DynamicSeq.create(), reference);
    var local = signatureResolver.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    decl.result = decl.result.accept(signatureResolver, local._2);
    var bodyResolver = new ExprResolver(false, signatureResolver.allowedLevels(), reference);
    decl.fields.forEach(field -> {
      var fieldLocal = bodyResolver.resolveParams(field.telescope, local._2);
      field.telescope = fieldLocal._1;
      field.result = field.result.accept(bodyResolver, fieldLocal._2);
      field.body = field.body.map(e -> e.accept(bodyResolver, fieldLocal._2));
      field.clauses = field.clauses.map(clause -> PatResolver.INSTANCE.matchy(clause, fieldLocal._2, bodyResolver));
    });
    info.declGraph().suc(decl).appendAll(reference);
    return Unit.unit();
  }

  /** @apiNote Note that this function MUTATES the decl. */
  @Override public Unit visitFn(Decl.@NotNull FnDecl decl, ResolveInfo info) {
    var reference = DynamicSeq.<Stmt>create();
    var signatureResolver = new ExprResolver(true, DynamicSeq.create(), reference);
    var local = signatureResolver.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    decl.result = decl.result.accept(signatureResolver, local._2);
    var bodyResolver = new ExprResolver(false, signatureResolver.allowedLevels(), reference);
    decl.body = decl.body.map(
      expr -> expr.accept(bodyResolver, local._2),
      pats -> pats.map(clause -> PatResolver.INSTANCE.matchy(clause, local._2, bodyResolver)));
    info.declGraph().suc(decl).appendAll(reference);
    return Unit.unit();
  }

  @Override public Unit visitPrim(@NotNull Decl.PrimDecl decl, ResolveInfo info) {
    var resolver = new ExprResolver(false, DynamicSeq.create(), DynamicSeq.create());
    var local = resolver.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    if (decl.result != null) decl.result = decl.result.accept(resolver, local._2);
    info.declGraph().suc(decl).appendAll(resolver.reference());
    return Unit.unit();
  }

  @Override public Unit visitLevels(Generalize.@NotNull Levels levels, ResolveInfo info) {
    return Unit.unit();
  }

  @Override public Unit visitExample(Sample.@NotNull Working example, ResolveInfo info) {
    visitSample(example, info);
    return Unit.unit();
  }

  @Override public Unit visitCounterexample(Sample.@NotNull Counter example, ResolveInfo info) {
    visitSample(example, info);
    return Unit.unit();
  }

  private void visitSample(@NotNull Sample sample, ResolveInfo info) {
    var delegate = sample.delegate();
    var delegateInfo = new ResolveInfo(info.opSet(), MutableGraph.create(), MutableGraph.create());
    delegate.accept(this, delegateInfo);
    // little hacky: transfer dependencies from `delegate` to `sample`
    info.sampleGraph().suc(sample).appendAll(delegateInfo.declGraph().suc(delegate));
  }

  @Override public Unit visitRemark(@NotNull Remark remark, ResolveInfo info) {
    info.sampleGraph().suc(remark).appendAll(remark.doResolve(info));
    return Unit.unit();
  }
}

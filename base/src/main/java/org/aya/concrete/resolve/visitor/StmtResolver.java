// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.visitor;

import kala.collection.mutable.Buffer;
import kala.control.Either;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Unit;
import kala.value.Ref;
import org.aya.api.error.Reporter;
import org.aya.api.ref.DefVar;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.error.UnknownOperatorError;
import org.aya.concrete.stmt.*;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Resolves expressions inside stmts, after {@link StmtShallowResolver}
 *
 * @author re-xyr, ice1000
 * @see StmtShallowResolver
 * @see ExprResolver
 */
public final class StmtResolver implements Stmt.Visitor<BinOpSet, Unit> {
  public static final @NotNull StmtResolver INSTANCE = new StmtResolver();

  private StmtResolver() {
  }

  @Override public Unit visitModule(Command.@NotNull Module mod, BinOpSet opSet) {
    visitAll(mod.contents(), opSet);
    return Unit.unit();
  }

  @Override public Unit visitImport(Command.@NotNull Import cmd, BinOpSet opSet) {
    return Unit.unit();
  }

  @Override public Unit visitOpen(Command.@NotNull Open cmd, BinOpSet opSet) {
    return Unit.unit();
  }

  @Override public Unit visitBind(Command.@NotNull Bind bind, BinOpSet opSet) {
    var ctx = bind.context().value;
    if (ctx == null) throw new IllegalStateException("no shallow resolver?");
    var op = resolveOp(opSet.reporter(), ctx, bind.op());
    var target = resolveOp(opSet.reporter(), ctx, bind.target());
    bind.resolvedOp().value = op._2;
    bind.resolvedTarget().value = target._2;
    opSet.bind(op, bind.pred(), target, bind.sourcePos());
    return Unit.unit();
  }

  @Override public Unit visitRemark(Literate.@NotNull Remark remark, BinOpSet binOpSet) {
    // TODO[remark]
    return Unit.unit();
  }

  private @NotNull Tuple2<String, @NotNull OpDecl>
  resolveOp(@NotNull Reporter reporter, @NotNull Context ctx, @NotNull Either<QualifiedID, OpDecl> idOrOp) {
    if (idOrOp.isRight()) {
      var builtin = idOrOp.getRightValue();
      return Tuple.of(Objects.requireNonNull(builtin.asOperator()).name(), builtin);
    }
    var id = idOrOp.getLeftValue();
    var var = ctx.get(id);
    if (var instanceof DefVar<?, ?> defVar && defVar.concrete instanceof OpDecl op) {
      return Tuple.of(defVar.name(), op);
    }
    reporter.report(new UnknownOperatorError(id.sourcePos(), id.join()));
    throw new Context.ResolvingInterruptedException();
  }

  @Override public Unit visitCtor(@NotNull Decl.DataCtor ctor, BinOpSet binOpSet) {
    throw new UnsupportedOperationException();
  }

  @Override public Unit visitField(@NotNull Decl.StructField field, BinOpSet binOpSet) {
    throw new UnsupportedOperationException();
  }

  /** @apiNote Note that this function MUTATES the decl. */
  @Override public Unit visitData(Decl.@NotNull DataDecl decl, BinOpSet opSet) {
    var signatureResolver = new ExprResolver(true, Buffer.of());
    var local = signatureResolver.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    decl.result = decl.result.accept(signatureResolver, local._2);
    var bodyResolver = new ExprResolver(false, signatureResolver.allowedLevels());
    for (var ctor : decl.body) {
      var localCtxWithPat = new Ref<>(local._2);
      ctor.patterns = ctor.patterns.map(pattern -> PatResolver.INSTANCE.subpatterns(localCtxWithPat, pattern));
      var ctorLocal = bodyResolver.resolveParams(ctor.telescope, localCtxWithPat.value);
      ctor.telescope = ctorLocal._1;
      ctor.clauses = ctor.clauses.map(clause -> PatResolver.INSTANCE.matchy(clause, ctorLocal._2, bodyResolver));
    }
    return Unit.unit();
  }

  @Override public Unit visitStruct(Decl.@NotNull StructDecl decl, BinOpSet opSet) {
    var signatureResolver = new ExprResolver(true, Buffer.of());
    var local = signatureResolver.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    decl.result = decl.result.accept(signatureResolver, local._2);

    var bodyResolver = new ExprResolver(false, signatureResolver.allowedLevels());
    decl.fields.forEach(field -> {
      var fieldLocal = bodyResolver.resolveParams(field.telescope, local._2);
      field.telescope = fieldLocal._1;
      field.result = field.result.accept(bodyResolver, fieldLocal._2);
      field.body = field.body.map(e -> e.accept(bodyResolver, fieldLocal._2));
      field.clauses = field.clauses.map(clause -> PatResolver.INSTANCE.matchy(clause, fieldLocal._2, bodyResolver));
    });

    return Unit.unit();
  }

  /** @apiNote Note that this function MUTATES the decl. */
  @Override public Unit visitFn(Decl.@NotNull FnDecl decl, BinOpSet opSet) {
    var signatureResolver = new ExprResolver(true, Buffer.of());
    var local = signatureResolver.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    decl.result = decl.result.accept(signatureResolver, local._2);
    var bodyResolver = new ExprResolver(false, signatureResolver.allowedLevels());
    decl.body = decl.body.map(
      expr -> expr.accept(bodyResolver, local._2),
      pats -> pats.map(clause -> PatResolver.INSTANCE.matchy(clause, local._2, bodyResolver)));
    return Unit.unit();
  }

  @Override public Unit visitPrim(@NotNull Decl.PrimDecl decl, BinOpSet opSet) {
    var local = ExprResolver.NO_GENERALIZED.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    if (decl.result != null) decl.result = decl.result.accept(ExprResolver.NO_GENERALIZED, local._2);
    return Unit.unit();
  }

  @Override public Unit visitLevels(Generalize.@NotNull Levels levels, BinOpSet binOpSet) {
    return Unit.unit();
  }

  @Override public Unit visitExample(Sample.@NotNull Working example, BinOpSet binOpSet) {
    example.delegate().accept(this, binOpSet);
    return Unit.unit();
  }

  @Override public Unit visitCounterexample(Sample.@NotNull Counter example, BinOpSet binOpSet) {
    example.delegate().accept(this, binOpSet);
    return Unit.unit();
  }
}

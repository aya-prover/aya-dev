// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.visitor;

import org.aya.api.error.Reporter;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.concrete.Generalize;
import org.aya.concrete.QualifiedID;
import org.aya.concrete.Stmt;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.error.UnknownOperatorError;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.tuple.Unit;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves expressions inside stmts, after {@link StmtShallowResolver}
 *
 * @author re-xyr, iec1000
 */
public final class StmtResolver implements Stmt.Visitor<BinOpSet, Unit> {
  public static final @NotNull StmtResolver INSTANCE = new StmtResolver();

  private StmtResolver() {
  }

  @Override public Unit visitModule(Stmt.@NotNull ModuleStmt mod, BinOpSet opSet) {
    visitAll(mod.contents(), opSet);
    return Unit.unit();
  }

  @Override public Unit visitImport(Stmt.@NotNull ImportStmt cmd, BinOpSet opSet) {
    return Unit.unit();
  }

  @Override public Unit visitOpen(Stmt.@NotNull OpenStmt cmd, BinOpSet opSet) {
    return Unit.unit();
  }

  @Override public Unit visitBind(Stmt.@NotNull BindStmt bind, BinOpSet opSet) {
    var ctx = bind.context().value;
    if (ctx == null) throw new IllegalStateException("no shallow resolver?");
    var op = resolveOp(opSet.reporter(), ctx, bind.op());
    var target = resolveOp(opSet.reporter(), ctx, bind.target());
    bind.resolvedOp().value = op._2;
    bind.resolvedTarget().value = target._2;
    opSet.bind(op, bind.pred(), target, bind.sourcePos());
    return Unit.unit();
  }

  private @NotNull Tuple2<String, Decl.@NotNull OpDecl> resolveOp(@NotNull Reporter reporter,
                                                                  @NotNull Context ctx,
                                                                  @NotNull QualifiedID id) {
    var var = ctx.get(id);
    if (var instanceof DefVar<?, ?> defVar && defVar.concrete instanceof Decl.OpDecl op) {
      return Tuple.of(defVar.name(), op);
    }
    reporter.report(new UnknownOperatorError(id.sourcePos(), id.join()));
    throw new Context.ResolvingInterruptedException();
  }

  /** @apiNote Note that this function MUTATES the decl. */
  @Override public Unit visitData(Decl.@NotNull DataDecl decl, BinOpSet opSet) {
    var local = ExprResolver.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    decl.result = decl.result.resolve(local._2);
    for (var ctor : decl.body) {
      var localCtxWithPat = new Ref<>(local._2);
      ctor.patterns = ctor.patterns.map(pattern -> PatResolver.INSTANCE.subpatterns(localCtxWithPat, pattern));
      var ctorLocal = ExprResolver.resolveParams(ctor.telescope, localCtxWithPat.value);
      ctor.telescope = ctorLocal._1;
      ctor.clauses = ctor.clauses.map(clause -> PatResolver.INSTANCE.matchy(clause, ctorLocal._2));
    }
    return Unit.unit();
  }

  @Override public Unit visitStruct(Decl.@NotNull StructDecl decl, BinOpSet opSet) {
    var local = ExprResolver.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    decl.result = decl.result.resolve(local._2);

    decl.fields.forEach(field -> {
      var fieldLocal = ExprResolver.resolveParams(field.telescope, local._2);
      field.telescope = fieldLocal._1;
      field.result = field.result.resolve(fieldLocal._2);
      field.body = field.body.map(e -> e.resolve(fieldLocal._2));
      field.clauses = field.clauses.map(clause -> PatResolver.INSTANCE.matchy(clause, fieldLocal._2));
    });

    return Unit.unit();
  }

  /** @apiNote Note that this function MUTATES the decl. */
  @Override public Unit visitFn(Decl.@NotNull FnDecl decl, BinOpSet opSet) {
    var local = ExprResolver.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    decl.result = decl.result.resolve(local._2);
    decl.body = decl.body.map(
      expr -> expr.resolve(local._2),
      pats -> pats.map(clause -> PatResolver.INSTANCE.matchy(clause, local._2)));
    return Unit.unit();
  }

  @Override public Unit visitPrim(@NotNull Decl.PrimDecl decl, BinOpSet opSet) {
    var local = ExprResolver.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    if (decl.result != null) decl.result = decl.result.resolve(local._2);
    return Unit.unit();
  }

  @Override public Unit visitLevels(Generalize.@NotNull Levels levels, BinOpSet binOpSet) {
    return Unit.unit();
  }
}

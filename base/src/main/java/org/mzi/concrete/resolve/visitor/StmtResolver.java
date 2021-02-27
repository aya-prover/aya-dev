// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.visitor;

import org.glavo.kala.tuple.Unit;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Stmt;

/**
 * resolves expressions inside stmts, after StmtShallowResolveConsumer
 *
 * @author re-xyr
 */
public final class StmtResolver implements Stmt.Visitor<Unit, Unit> {
  public static final @NotNull StmtResolver INSTANCE = new StmtResolver();

  private StmtResolver() {
  }

  @Override
  public Unit visitModule(Stmt.@NotNull ModuleStmt mod, Unit unit) {
    visitAll(mod.contents(), unit);
    return unit;
  }

  @Override
  public Unit visitImport(Stmt.@NotNull ImportStmt cmd, Unit unit) {
    return Unit.unit();
  }

  @Override
  public Unit visitOpen(Stmt.@NotNull OpenStmt cmd, Unit unit) {
    return Unit.unit();
  }

  /** @apiNote Note that this function MUTATES the decl. */
  @Override
  public Unit visitDataDecl(Decl.@NotNull DataDecl decl, Unit unit) {
    throw new UnsupportedOperationException(); // TODO[xyr]: implement
  }

  /** @apiNote Note that this function MUTATES the decl. */
  @Override
  public Unit visitFnDecl(Decl.@NotNull FnDecl decl, Unit unit) {
    var local = ExprResolver.INSTANCE.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1.collect(ImmutableSeq.factory());
    decl.result = decl.result.resolve(local._2);
    decl.body = decl.body.resolve(local._2);
    return Unit.unit();
  }
}

// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.visitor;

import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Unit;
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
    var local = ExprResolver.INSTANCE.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    decl.result = decl.result.resolve(local._2);
    return decl.body.accept(new Decl.DataBody.Visitor<>() {
      @Override public Unit visitCtor(Decl.DataBody.@NotNull Ctors ctors, Unit unit) {
        for (var ctor : ctors.ctors()) {
          var ctorLocal = ExprResolver.INSTANCE.resolveParams(ctor.telescope, local._2);
          ctor.telescope = ctorLocal._1;
          ctor.clauses = ctor.clauses.stream()
            .map(clause -> clause.mapTerm(e -> e.resolve(ctorLocal._2)))
            .collect(Buffer.factory());
        }
        return unit;
      }

      @Override public Unit visitClause(Decl.DataBody.@NotNull Clauses clauses, Unit unit) {
        throw new UnsupportedOperationException();
      }
    }, unit);
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

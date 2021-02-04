// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.glavo.kala.Unit;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Stmt;
import org.mzi.core.def.Def;

public class StmtTycker implements Stmt.Visitor<Unit, Unit> {
  public final @NotNull Buffer<Def> defs = Buffer.of();
  @Override public Unit visitCmd(Stmt.@NotNull CmdStmt cmd, Unit unit) {
    return null;
  }

  @Override public Unit visitModule(Stmt.@NotNull ModuleStmt mod, Unit unit) {
    return null;
  }

  @Override public Unit visitDataDecl(Decl.@NotNull DataDecl decl, Unit unit) {
    return null;
  }

  @Override public Unit visitFnDecl(Decl.@NotNull FnDecl decl, Unit unit) {
    return null;
  }
}

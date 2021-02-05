// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.glavo.kala.Unit;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Reporter;
import org.mzi.concrete.Decl;

public class StmtTycker implements Decl.Visitor<Unit, Unit> {
  public final @NotNull Reporter reporter;

  public StmtTycker(@NotNull Reporter reporter) {
    this.reporter = reporter;
  }

  @Override public Unit visitDataDecl(Decl.@NotNull DataDecl decl, Unit unit) {
    // TODO
    return null;
  }

  @Override public Unit visitFnDecl(Decl.@NotNull FnDecl decl, Unit unit) {
    return null;
  }
}

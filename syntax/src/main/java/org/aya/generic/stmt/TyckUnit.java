// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic.stmt;

import org.aya.syntax.concrete.stmt.Command;
import org.aya.syntax.concrete.stmt.Generalize;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.decl.Decl;
import org.aya.syntax.concrete.stmt.decl.PrimDecl;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.error.SourceNode;
import org.jetbrains.annotations.NotNull;

public sealed interface TyckUnit extends SourceNode permits Stmt, Decl {
  static boolean needTyck(@NotNull TyckOrder unit, @NotNull ModulePath currentMod) {
    return needTyck(unit.unit(), currentMod);
  }
  static boolean needTyck(@NotNull TyckUnit unit, @NotNull ModulePath currentMod) {
    return switch (unit) {
      case PrimDecl prim -> prim.ref.isInModule(currentMod) && prim.ref.signature == null;
      case Decl decl -> decl.ref().isInModule(currentMod) && decl.ref().core == null;
      case Generalize _, Command _ -> false;
    };
  }
}

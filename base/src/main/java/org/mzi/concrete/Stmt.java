// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import asia.kala.collection.immutable.ImmutableList;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public sealed interface Stmt permits Decl, Stmt.CmdStmt {
  record CmdStmt(
    @NotNull Cmd cmd,
    @NotNull String qualifiedModuleName,
    @NotNull ImmutableList<@NotNull String> using,
    @NotNull ImmutableList<@NotNull String> hiding
  ) implements Stmt {
    /**
     * @author kiva
     */
    public enum Cmd {
      Open,
      Import,
    }
  }
}

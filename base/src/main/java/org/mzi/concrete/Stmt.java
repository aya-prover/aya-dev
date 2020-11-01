// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.SourcePos;

/**
 * @author kiva
 */
public sealed interface Stmt permits Decl, Stmt.CmdStmt {
  @Contract(pure = true) @NotNull SourcePos sourcePos();

  @Contract(pure = true) boolean isPublic();

  record CmdStmt(
    @NotNull SourcePos sourcePos,
    boolean isPublic,
    @NotNull Cmd cmd,
    @NotNull String qualifiedModuleName,
    @NotNull ImmutableSeq<@NotNull String> using,
    @NotNull ImmutableSeq<@NotNull String> hiding
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

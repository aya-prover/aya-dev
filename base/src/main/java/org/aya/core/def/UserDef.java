// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Problem;
import org.aya.concrete.stmt.Decl;
import org.aya.core.sort.Sort;
import org.aya.core.term.Term;
import org.aya.tyck.ExprTycker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Definitions by user.
 *
 * @author ice1000
 */
public sealed abstract class UserDef extends TopLevelDef permits DataDef, FnDef, StructDef {
  /**
   * In case of counterexamples, this field will be assigned.
   *
   * @see org.aya.tyck.StmtTycker#tyck(Decl, ExprTycker)
   */
  public @Nullable ImmutableSeq<Problem> problems;

  protected UserDef(@NotNull ImmutableSeq<Term.Param> telescope, @NotNull Term result, @NotNull ImmutableSeq<Sort.LvlVar> levels) {
    super(telescope, result, levels);
  }
}

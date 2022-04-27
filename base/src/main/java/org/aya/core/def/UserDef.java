// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.TopTeleDecl;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.tyck.ExprTycker;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Definitions by user.
 *
 * @author ice1000
 */
public sealed abstract class UserDef extends TopLevelDef permits FnDef, UserDef.Type {
  /**
   * In case of counterexamples, this field will be assigned.
   *
   * @see org.aya.tyck.StmtTycker#tyck(TopTeleDecl, ExprTycker)
   */
  public @Nullable ImmutableSeq<Problem> problems;

  protected UserDef(@NotNull ImmutableSeq<Term.Param> telescope, @NotNull Term result) {
    super(telescope, result);
  }

  public static abstract sealed class Type extends UserDef permits DataDef {
    public final int resultLevel;

    protected Type(@NotNull ImmutableSeq<Term.Param> telescope, int resultLevel) {
      super(telescope, new FormTerm.Univ(resultLevel));
      this.resultLevel = resultLevel;
    }
  }
}

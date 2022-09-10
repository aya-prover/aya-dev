// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.Decl;
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
   * @see org.aya.tyck.StmtTycker#tyck(Decl, ExprTycker)
   */
  public @Nullable ImmutableSeq<Problem> problems;

  protected UserDef(@NotNull ImmutableSeq<Term.Param> telescope, @NotNull Term result) {
    super(telescope, result);
  }

  public static abstract sealed class Type extends UserDef permits DataDef, StructDef {
    public final FormTerm.Sort resultLevel;

    protected Type(@NotNull ImmutableSeq<Term.Param> telescope, FormTerm.Sort resultLevel) {
      super(telescope, resultLevel);
      this.resultLevel = resultLevel;
    }
  }
}

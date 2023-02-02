// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.Decl;
import org.aya.core.term.SortTerm;
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
public sealed abstract class UserDef<Ret extends Term>
  extends TopLevelDef<Ret> permits ClassDef.Member, FnDef, UserDef.Type {
  /**
   * In case of counterexamples, this field will be assigned.
   *
   * @see org.aya.tyck.StmtTycker#tyck(Decl, ExprTycker)
   */
  public @Nullable ImmutableSeq<Problem> problems;

  protected UserDef(@NotNull ImmutableSeq<Term.Param> telescope, @NotNull Ret result) {
    super(telescope, result);
  }

  public static abstract sealed class Type extends UserDef<SortTerm> permits DataDef, StructDef {

    protected Type(@NotNull ImmutableSeq<Term.Param> telescope, SortTerm result) {
      super(telescope, result);
    }
  }
}

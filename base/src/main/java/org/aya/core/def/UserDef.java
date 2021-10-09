// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Problem;
import org.aya.core.sort.Sort;
import org.aya.core.term.Term;
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
   * @see org.aya.concrete.stmt.Sample#tyck(org.aya.api.error.Reporter, org.aya.tyck.trace.Trace.Builder)
   */
  public @Nullable ImmutableSeq<Problem> problems;

  protected UserDef(@NotNull ImmutableSeq<Term.Param> telescope, @NotNull Term result, @NotNull ImmutableSeq<Sort.LvlVar> levels) {
    super(telescope, result, levels);
  }
}

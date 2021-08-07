// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Problem;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Top-level definitions by user.
 *
 * @author ice1000
 */
public sealed abstract class TopLevelDef implements Def permits DataDef, FnDef, StructDef {
  public final @NotNull ImmutableSeq<Term.Param> telescope;
  public final @NotNull Term result;

  /** In case of counterexamples, this field will be assigned */
  public @Nullable ImmutableSeq<Problem> problems;

  protected TopLevelDef(@NotNull ImmutableSeq<Term.Param> telescope, @NotNull Term result) {
    this.telescope = telescope;
    this.result = result;
  }

  @Override public @NotNull ImmutableSeq<Term.Param> telescope() {
    return telescope;
  }

  @Override public @NotNull Term result() {
    return result;
  }
}

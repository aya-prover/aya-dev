// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.tyck.sort.LevelEqn;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;

public record MetaContext(
  @NotNull Reporter reporter,
  LevelEqn.@NotNull Set levelEqns,
  MutableMap<CallTerm.Hole, Term> solutions
) {
  public MetaContext(@NotNull Reporter reporter) {
    this(reporter, new LevelEqn.Set(Buffer.of(), Buffer.of()), new MutableHashMap<>());
  }

  public void report(@NotNull Problem problem) {
    reporter.report(problem);
  }
}

// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.api.error.Reporter;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.Term;
import org.mzi.tyck.sort.LevelEqn;

public record MetaContext(
  @NotNull Reporter reporter,
  LevelEqn.@NotNull Set levelEqns,
  MutableMap<AppTerm.HoleApp, Term> solutions
) {
  public MetaContext(@NotNull Reporter reporter) {
    this(reporter, new LevelEqn.Set(Buffer.of(), Buffer.of()), new MutableHashMap<>());
  }

  public void report(@NotNull Problem problem) {
    reporter.report(problem);
  }
}

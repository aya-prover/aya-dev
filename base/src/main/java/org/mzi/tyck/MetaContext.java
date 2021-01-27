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

public class MetaContext {
  private final LevelEqn.@NotNull Set levelEqns;
  private final @NotNull Reporter reporter;
  private final MutableMap<AppTerm.HoleApp, Term> solutions;

  public MetaContext(@NotNull Reporter reporter) {
    this.reporter = reporter;
    levelEqns = new LevelEqn.Set(reporter, Buffer.of(), Buffer.of());
    solutions = new MutableHashMap<>();
  }

  public LevelEqn.@NotNull Set levelEqns() {
    return levelEqns;
  }

  public void report(@NotNull Problem problem) {
    reporter.report(problem);
  }

  public @NotNull MutableMap<AppTerm.HoleApp, Term> solutions() {
    return solutions;
  }
}

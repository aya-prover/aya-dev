// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.core.term.Term;
import org.aya.core.visitor.UsageCounter;
import org.aya.tyck.error.RecursiveSolutionError;
import org.aya.tyck.sort.LevelEqn;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.control.Option;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public record MetaContext(
  @NotNull Reporter reporter,
  LevelEqn.@NotNull Set levelEqns,
  MutableMap<Var, Term> solutions
) {
  public MetaContext(@NotNull Reporter reporter) {
    this(reporter, new LevelEqn.Set(Buffer.of(), Buffer.of()), new MutableHashMap<>());
  }

  public void solve(@NotNull Var v, @NotNull Term t, @NotNull SourcePos pos) {
    var usages = new UsageCounter(v);
    t.accept(usages, Unit.unit());
    if (usages.usageCount() > 0) {
      report(new RecursiveSolutionError(v, t, pos));
      throw new ExprTycker.TyckInterruptedException();
    }
    solutions.put(v, t);
  }

  public @NotNull Option<Term> solution(@NotNull Var v) {
    return solutions.getOption(v);
  }

  public void report(@NotNull Problem problem) {
    reporter.report(problem);
  }
}

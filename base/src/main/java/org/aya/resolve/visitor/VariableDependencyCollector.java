// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableSet;
import org.aya.util.error.PosedUnaryOperator;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.CyclicDependencyError;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.ref.GeneralizedVar;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class VariableDependencyCollector {
  private final Map<GeneralizedVar, ImmutableSeq<GeneralizedVar>> dependencies = new HashMap<>();
  private final Reporter reporter;
  private final MutableSet<GeneralizedVar> visiting = MutableSet.create();

  public VariableDependencyCollector(Reporter reporter) {
    this.reporter = reporter;
  }

  public void registerVariable(GeneralizedVar var) {
    if (dependencies.containsKey(var)) return;

    // If var is already being visited, we found a cycle.
    if (!visiting.add(var)) {
      reporter.report(new CyclicDependencyError(var.sourcePos(), var));
      throw new Context.ResolvingInterruptedException();
    }

    var deps = collectReferences(var);
    dependencies.put(var, deps);
    visiting.remove(var);

    // Recursively register dependencies
    for (var dep : deps) registerVariable(dep);
  }

  public ImmutableSeq<GeneralizedVar> getDependencies(GeneralizedVar var) {
    return dependencies.getOrDefault(var, ImmutableSeq.empty());
  }

  private ImmutableSeq<GeneralizedVar> collectReferences(GeneralizedVar var) {
    var type = var.owner.type;
    var collector = new StaticGeneralizedVarCollector();
    type.descent(collector);
    return collector.getCollected();
  }

  private static class StaticGeneralizedVarCollector implements PosedUnaryOperator<Expr> {
    private final MutableList<GeneralizedVar> collected = MutableList.create();

    @Override
    public @NotNull Expr apply(@NotNull SourcePos pos, @NotNull Expr expr) {
      if (expr instanceof Expr.Ref ref && ref.var() instanceof GeneralizedVar gvar) {
        collected.append(gvar);
      }
      return expr.descent(this);
    }

    public ImmutableSeq<GeneralizedVar> getCollected() {
      return collected.toImmutableSeq();
    }
  }
}

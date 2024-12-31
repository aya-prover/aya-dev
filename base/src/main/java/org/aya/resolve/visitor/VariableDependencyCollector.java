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

/**
 * Collects dependency information for generalized variables using DFS on their types.
 * 
 * 1. A variable's type may reference other generalized variables; we record those as dependencies.
 * 2. If we revisit a variable already on the DFS stack ("visiting" set), that indicates
 *    a cyclic dependency, and we report an error.
 * 3. Once a variable is fully processed, it goes into the "visited" set; future registrations
 *    of the same variable skip repeated traversal.
 * 
 * Pitfalls & Notes:
 *   - A single variable (e.g. “A”) should be registered once, to avoid duplication.
 *   - Attempting to re-scan or re-introduce “A” in another variable’s context can cause
 *     confusion or potential cycles. So we do all dependency scans here, at declaration time.
 *   - Any reference to a variable out of scope is handled as an error in the resolver
 *     if it’s not in the allowedGeneralizes map.
 */
public final class VariableDependencyCollector {
  private final Map<GeneralizedVar, ImmutableSeq<GeneralizedVar>> dependencies = new HashMap<>();
  private final Reporter reporter;
  private final MutableSet<GeneralizedVar> visiting = MutableSet.create();
  private final MutableSet<GeneralizedVar> visited = MutableSet.create();

  public VariableDependencyCollector(Reporter reporter) {
    this.reporter = reporter;
  }

  public void registerVariable(GeneralizedVar var) {
    if (visited.contains(var)) return;

    // If var is already being visited in current DFS path, we found a cycle
    if (!visiting.add(var)) {
      reporter.report(new CyclicDependencyError(var.sourcePos(), var));
      throw new Context.ResolvingInterruptedException();
    }

    var deps = collectReferences(var);
    dependencies.put(var, deps);

    // Recursively register dependencies
    for (var dep : deps) {
      registerVariable(dep);
    }

    visiting.remove(var);
    visited.add(var);
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

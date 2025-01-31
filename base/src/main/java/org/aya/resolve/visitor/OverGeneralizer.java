// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.mutable.MutableList;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.CyclicDependencyError;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.ref.GeneralizedVar;
import org.jetbrains.annotations.NotNull;

/// Collects dependency information for generalized variables using DFS on their types.
///
/// 1. A variable's type may reference other generalized variables; we record those as dependencies.
/// 2. If we revisit a variable already on the DFS stack [#currentPath], that indicates
///    a cyclic dependency, and we report an error.
/// 3. Once a variable is fully processed, it goes into the [#introduceDependency] method; future registrations
///    of the same variable skip repeated traversal using [#contains].
public abstract class OverGeneralizer {
  private final @NotNull Context reporter;
  public final @NotNull MutableList<GeneralizedVar> currentPath = MutableList.create();

  public OverGeneralizer(@NotNull Context reporter) { this.reporter = reporter; }
  protected abstract boolean contains(@NotNull GeneralizedVar var);
  protected abstract void introduceDependency(@NotNull GeneralizedVar var, @NotNull Expr.Param param);

  public final void introduceDependencies(@NotNull GeneralizedVar var, @NotNull Expr.Param param) {
    if (contains(var)) return;

    // If var is already being visited in current DFS path, we found a cycle
    if (currentPath.contains(var)) {
      // Find cycle start index
      var cycleStart = currentPath.indexOf(var);
      if (cycleStart < 0) cycleStart = 0;
      var cyclePath = currentPath.view().drop(cycleStart).appended(var);
      reporter.reportAndThrow(new CyclicDependencyError(var.sourcePos(), var, cyclePath.toImmutableSeq()));
    }

    currentPath.append(var);
    // Introduce dependencies first
    var.owner.dependencies.forEach(this::introduceDependencies);

    // Now introduce the variable itself
    introduceDependency(var, param);
    currentPath.removeLast();
  }
}

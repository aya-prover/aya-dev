// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import java.util.function.Consumer;

import kala.collection.CollectionView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.CyclicDependencyError;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.ref.GeneralizedVar;
import org.aya.syntax.ref.GenerateKind;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.error.PosedUnaryOperator;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Collects dependency information for generalized variables using DFS on their types.
///
/// 1. A variable's type may reference other generalized variables; we record those as dependencies.
/// 2. If we revisit a variable already on the DFS stack ("visiting" set), that indicates
///    a cyclic dependency, and we report an error.
/// 3. Once a variable is fully processed, it goes into the "visited" set; future registrations
///    of the same variable skip repeated traversal.
///
/// Pitfalls & Notes:
/// - A single variable (e.g. “A”) should be registered once, to avoid duplication.
/// - Attempting to re-scan or re-introduce “A” in another variable’s context can cause
///   confusion or potential cycles. So we do all dependency scans here, at declaration time.
/// - Any reference to a variable out of scope is handled as an error in the resolver
///   if it’s not in the allowedGeneralizes map.
public final class OverGeneralizer {
  private final @NotNull Context reporter;
  private final @NotNull MutableSet<GeneralizedVar> visiting = MutableSet.create();
  private final @NotNull MutableList<GeneralizedVar> currentPath = MutableList.create();
  private final @NotNull MutableMap<GeneralizedVar, Expr.Param> allowedGeneralizes = MutableLinkedHashMap.of();

  public OverGeneralizer(@NotNull Context reporter) {
    this.reporter = reporter;
  }

  public @NotNull LocalVar register(GeneralizedVar var, @NotNull Consumer<GeneralizedVar> onGenVarVisited) {
    if (allowedGeneralizes.containsKey(var)) return allowedGeneralizes.get(var).ref();

    // If var is already being visited in current DFS path, we found a cycle
    if (!visiting.add(var)) {
      // Find cycle start index
      var cycleStart = currentPath.indexOf(var);
      var cyclePath = currentPath.view().drop(cycleStart).appended(var);
      visiting.clear();
      reporter.reportAndThrow(new CyclicDependencyError(var.sourcePos(), var, cyclePath.toImmutableSeq()));
    }

    currentPath.append(var);
    var deps = collectReferences(var);
    var.owner.dependencies = deps;

    // Recursively register dependencies
    for (var dep : deps) register(dep, onGenVarVisited);

    currentPath.removeLast();
    visiting.remove(var);
    var param = var.toParam(false);
    allowedGeneralizes.put(var, param);
    onGenVarVisited.accept(var);
    return param.ref();
  }

  public @NotNull CollectionView<Expr.Param> allowedGeneralizeParams() {
    return allowedGeneralizes.valuesView();
  }

  public @NotNull CollectionView<GeneralizedVar> allowedGeneralizeVars() {
    return allowedGeneralizes.keysView();
  }

  public @Nullable Expr.Param getParam(@NotNull GeneralizedVar var) {
    return allowedGeneralizes.getOrNull(var);
  }

  private @NotNull ImmutableSeq<GeneralizedVar> collectReferences(GeneralizedVar var) {
    var type = var.owner.type;
    var collector = new StaticGeneralizedVarCollector();
    type.descent(collector);
    return collector.collected.toImmutableSeq();
  }

  private static class StaticGeneralizedVarCollector implements PosedUnaryOperator<Expr> {
    public final MutableList<GeneralizedVar> collected = MutableList.create();
    @Override public @NotNull Expr apply(@NotNull SourcePos pos, @NotNull Expr expr) {
      if (expr instanceof Expr.Ref ref) {
        var var = ref.var();
        if (var instanceof LocalVar local && local.generateKind() instanceof GenerateKind.Generalized(var origin)) {
          collected.append(origin);
          collected.appendAll(origin.owner.dependencies);
        }
      }
      return expr.descent(this);
    }
  }
}

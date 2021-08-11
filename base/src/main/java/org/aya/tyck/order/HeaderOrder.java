// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.order;

import kala.collection.Seq;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableSet;
import org.aya.core.def.Def;
import org.aya.core.visitor.RefFinder;
import org.jetbrains.annotations.NotNull;

/**
 * Generate the order of dependency of headers, fail if a cycle occurs.
 *
 * @author re-xyr
 */
public final class HeaderOrder {
  private static void visit(Def def, MutableSet<Def> visited, MutableSet<Def> inStack, Buffer<Def> order) {
    if (inStack.contains(def))
      throw new IllegalStateException("Circular reference in definition header."); // TODO[xyr]: report instead of throw
    visited.add(def);
    inStack.add(def);
    order.append(def);
    var references = Buffer.<Def>of();
    def.accept(RefFinder.HEADER_ONLY, references);
    for (var nextDef : references) {
      if (visited.contains(nextDef)) continue;
      visit(nextDef, visited, inStack, order);
    }
    inStack.remove(def);
  }

  public static @NotNull Buffer<Def> genHeaderOrder(@NotNull Seq<Def> defs) {
    var visited = MutableSet.<Def>of();
    var inStack = MutableSet.<Def>of();
    var order = Buffer.<Def>of();
    for (var def : defs) {
      if (visited.contains(def)) continue;
      visit(def, visited, inStack, order);
    }
    return order;
  }
}

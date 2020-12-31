// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.order;

import asia.kala.collection.Seq;
import asia.kala.collection.mutable.Buffer;
import asia.kala.collection.mutable.MutableHashMap;
import asia.kala.collection.mutable.MutableMap;
import asia.kala.collection.mutable.MutableSet;
import org.jetbrains.annotations.Nullable;
import org.mzi.core.def.Def;
import org.mzi.core.visitor.RefFinder;

/**
 * Generate the order of dependency of both headers and bodies. Each SCC is in one subgroup.
 * @author re-xyr
 */
public final class BodyOrder {
  private static void visit(Def def,
                            MutableSet<Def> visited,
                            MutableSet<Def> inStack,
                            Buffer<Def> stack,
                            MutableMap<Def, Integer> dfn,
                            MutableMap<Def, Integer> low,
                            int n,
                            Buffer<Buffer<Def>> order) {
    dfn.put(def, n);
    low.put(def, n);
    inStack.add(def);
    stack.prepend(def);
    var references = Buffer.<Def>of();
    def.accept(RefFinder.HEADER_AND_BODY, references);
    for (var ref : references) {
      if (inStack.contains(ref)) {
        low.put(def, Math.min(low.get(def), dfn.get(ref)));
      } else {
        visit(ref, visited, inStack, stack, dfn, low, n + 1, order);
        low.put(def, Math.min(low.get(def), low.get(ref)));
      }
    }
    if (low.get(def).equals(dfn.get(def))) {
      var scc = Buffer.<Def>of();
      while (stack.first() != def) {
        scc.append(stack.first());
        stack.dropInPlace(1);
      }
      order.append(HeaderOrder.genHeaderOrder(scc));
    }
  }

  public static @Nullable Buffer<Buffer<Def>> genBodyOrder(Seq<Def> defs) {
    var visited = MutableSet.<Def>of();
    var inStack = MutableSet.<Def>of();
    var stack = Buffer.<Def>of();
    var dfn = new MutableHashMap<Def, Integer>();
    var low = new MutableHashMap<Def, Integer>();
    var order = Buffer.<Buffer<Def>>of();
    for (var def : defs) {
      if (visited.contains(def)) continue;
      visit(def, visited, inStack, stack, dfn, low, 0, order);
    }
    return order;
  }
}

// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify.level;

import kala.collection.SeqLike;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import org.aya.core.sort.Sort;
import org.aya.core.sort.Sort.LvlVar;
import org.aya.generic.Level;
import org.aya.tyck.unify.level.LevelEqnSet.Eqn;
import org.aya.util.Ordering;
import org.jetbrains.annotations.NotNull;

/**
 * @author danihao123, ice1000
 */
public class LevelSolver {
  public static class UnsatException extends Exception {
  }

  static final int INF = 100000000;
  static final int LOW_BOUND = INF;
  int nodeSize; // the number of nodes in the graph

  boolean floyd(int[][] d) { // return true when it's satisfied
    for (int k = 0; k <= nodeSize; k++)
      for (int i = 0; i <= nodeSize; i++)
        for (int j = 0; j <= nodeSize; j++)
          d[i][j] = Math.min(d[i][j], d[i][k] + d[k][j]);
    for (int i = 0; i <= nodeSize; i++) if (d[i][i] < 0) return true;
    for (var nu : unfreeNodes) {
      int u = graphMap.get(nu);
      if (d[u][0] < 0) return true;
      if (d[0][u] < LOW_BOUND / 2) return true;
      for (var nv : unfreeNodes) {
        int v = graphMap.get(nv);
        if (u != v && d[u][v] < LOW_BOUND / 2) return true;
      }
      for (int v = 1; v <= nodeSize; v++) {
        if (d[u][v] < 0) return true;
      }
    }
    return false;
  }

  void addEdge(int[][] g, int u, int v, int dist) {
    g[u][v] = Math.min(g[u][v], dist);
  }

  private final MutableSet<LvlVar> unfreeNodes = MutableSet.of();
  private final MutableSet<LvlVar> freeNodes = MutableSet.of();
  private final MutableMap<LvlVar, Integer> graphMap = MutableMap.create();
  private final MutableMap<LvlVar, Integer> defaultValues = MutableMap.create();
  public final Buffer<Eqn> avoidableEqns = Buffer.create();

  private void genGraphNode(SeqLike<Level<LvlVar>> l) {
    for (var e : l) {
      if (e instanceof Level.Reference<LvlVar> th) {
        graphMap.put(th.ref(), ++nodeSize);
      }
    }
  }

  /** @return true if fail */
  private boolean dealSingleLt(int[][] g, Level<LvlVar> a, Level<LvlVar> b) {
    if (a instanceof Level.Constant<LvlVar> ca) {
      if (b instanceof Level.Constant<LvlVar> cb) {
        return ca.value() > cb.value();
      } else if (b instanceof Level.Reference<LvlVar> rb) {
        // if(!rb.ref().free()) return;
        int u = ca.value();
        int v = rb.lift();
        int x = graphMap.get(rb.ref());
        addEdge(g, x, 0, v - u);
      }
    } else if (a instanceof Level.Reference<LvlVar> ra) {
      // if(!ra.ref().free()) return;
      int x = graphMap.get(ra.ref());
      int u = ra.lift();
      if (b instanceof Level.Constant<LvlVar> cb) {
        int v = cb.value();
        addEdge(g, 0, x, v - u);
      } else if (b instanceof Level.Reference<LvlVar> rb) {
        // if(!rb.ref().free()) return;
        int y = graphMap.get(rb.ref());
        int v = rb.lift();
        addEdge(g, y, x, v - u);
      }
    }
    return false;
  }

  void prepareGraphNode(int[][] g, SeqLike<Level<LvlVar>> l) {
    for (var e : l) {
      if (e instanceof Level.Reference<LvlVar> th) {
        int defaultValue = -th.lift();
        int u = graphMap.get(th.ref());
        if (th.ref().free()) {
          // addEdge(g, u, 0, -defaultValue);
          defaultValues.put(th.ref(), 0);
          freeNodes.add(th.ref());
          // Universe level can't be inf, homotopy can
          // Now there are no homotopy level
          addEdge(g, 0, u, LOW_BOUND);
        } else {
          unfreeNodes.add(th.ref());
        }
      }
    }
  }

  private int[][] dfs(SeqLike<Eqn> l, int pos, int[][] g) throws UnsatException {
    if (l.sizeLessThanOrEquals(pos)) {
      if (floyd(g)) {
        throw new UnsatException();
      } else {
        return g;
      }
    }
    var th = l.get(pos);
    var lhsVar = th.lhs().levels();
    var rhsVar = th.rhs().levels();
    if (lhsVar.isEmpty() || rhsVar.isEmpty()) return dfs(l, pos + 1, g);
    for (var max : rhsVar) {
      var gg = new int[nodeSize + 1][nodeSize + 1];
      for (int i = 0; i <= nodeSize; i++) {
        if (nodeSize + 1 >= 0) System.arraycopy(g[i], 0, gg[i], 0, nodeSize + 1);
      }
      for (var v : lhsVar) dealSingleLt(gg, v, max);
      for (var v : rhsVar) dealSingleLt(gg, v, max);
      try {
        return dfs(l, pos + 1, gg);
      } catch (UnsatException ignored) {
      }
    }
    throw new UnsatException();
  }

  public void solve(@NotNull LevelEqnSet eqns) throws UnsatException {
    var equations = eqns.eqns();
    nodeSize = 0;
    for (var e : equations) {
      genGraphNode(e.lhs().levels());
      genGraphNode(e.rhs().levels());
    }
    var g = new int[nodeSize + 1][nodeSize + 1];
    for (int i = 0; i <= nodeSize; i++) {
      for (int j = 0; j <= nodeSize; j++) {
        if (i == j) g[i][j] = 0;
        else g[i][j] = INF;
      }
    }
    for (var e : equations) {
      prepareGraphNode(g, e.lhs().levels());
      prepareGraphNode(g, e.rhs().levels());
    }
    var specialEq = Buffer.<Eqn>create();
    var equationsImm = equations.toImmutableSeq();
    var hasError = equationsImm
      // Do NOT make this lazy -- the `populate` function has side effects
      // We need to run populate on all equations
      .map(e -> populate(g, specialEq, e, true))
      .anyMatch(b -> b);
    if (hasError || floyd(g)) throw new UnsatException();
    hasError = equationsImm
      .map(e -> populate(g, specialEq, e, false))
      .anyMatch(b -> b);
    if (hasError || floyd(g))
      throw new UnsatException();
    var gg = dfs(specialEq, 0, g);
    for (var name : freeNodes) {
      int u = graphMap.get(name);
      int thDefault = defaultValues.get(name);
      int upperBound = gg[0][u];
      if (upperBound >= thDefault) {
        addEdge(gg, u, 0, thDefault);
        floyd(gg);
        upperBound = gg[0][u];
      }
      int lowerBound = -gg[u][0];
      if (lowerBound < 0) lowerBound = 0;
      var upperNodes = Buffer.<Level<LvlVar>>create();
      var lowerNodes = Buffer.<Level<LvlVar>>create();
      for (var nu : unfreeNodes) {
        int v = graphMap.get(nu);
        if (gg[v][u] != INF) upperNodes.append(new Level.Reference<>(nu, gg[v][u]));
        if (gg[u][v] < LOW_BOUND / 2) lowerNodes.append(new Level.Reference<>(nu, -gg[u][v]));
      }
      var retList = Buffer.<Level<LvlVar>>create();
      if (!lowerNodes.isEmpty() || upperNodes.isEmpty()) {
        if (lowerBound != 0 || lowerNodes.isEmpty()) retList.append(new Level.Constant<>(lowerBound));
        retList.appendAll(lowerNodes);
      } else {
        int minv = upperBound;
        for (var _l : upperNodes) {
          if (_l instanceof Level.Reference<LvlVar> l) minv = Math.min(minv, l.lift());
        }
        retList.append(new Level.Constant<>(minv));
      }
      eqns.solution().put(name, new Sort(retList.toImmutableSeq()));
    }
  }

  /** @return true if fail */
  private boolean populate(int[][] g, Buffer<Eqn> specialEq, Eqn e, boolean complex) {
    var lhs = e.lhs();
    var rhs = e.rhs();
    return switch (e.cmp()) {
      case Gt -> populateLt(g, specialEq, e, rhs, lhs, complex);
      case Lt -> populateLt(g, specialEq, e, lhs, rhs, complex);
      case Eq -> populateLt(g, specialEq, e, rhs, lhs, complex)
        && populateLt(g, specialEq, e, lhs, rhs, complex);
    };
  }

  /** @return true if fail */
  private boolean populateLt(int[][] g, Buffer<Eqn> specialEq, Eqn e, Sort lhs, Sort rhs, boolean complex) {
    if (complex && rhs.levels().sizeGreaterThan(1)) return false;
    var lhsLevels = lhs.levels().filter(vr -> {
      if (vr instanceof Level.Reference<LvlVar> ref) {
        var th = ref.ref();
        for (var vp : rhs.levels()) {
          if (vp instanceof Level.Reference<LvlVar> __r) {
            var tp = __r.ref();
            if (g[graphMap.get(tp)][graphMap.get(th)] + ref.lift() - __r.lift() <= 0)
              return false;
          }
        }
      }
      return true;
    });
    var rhsLevels = Buffer.<Level<LvlVar>>create();
    for (var vr : rhs.levels()) {
      var insert = true;
      if (vr instanceof Level.Reference<LvlVar> ref) {
        var th = ref.ref();
        if (!th.free()) {
          insert = false;
          if (lhsLevels.anyMatch(left -> dealSingleLt(g, left, vr)))
            return true;
        }
      }
      if (insert) rhsLevels.append(vr);
    }
    if (lhsLevels.isEmpty() || rhsLevels.isEmpty()) return false;
    if (lhsLevels.sizeEquals(1) && rhsLevels.sizeGreaterThan(1)) {
      var left = lhsLevels.get(0);
      if (left instanceof Level.Constant<LvlVar> constant && constant.value() == 0) {
        avoidableEqns.append(e);
        return false;
      }
      return rhsLevels.anyMatch(right -> dealSingleLt(g, left, right));
    }
    if (rhsLevels.sizeEquals(1)) {
      var right = rhsLevels.get(0);
      if (right instanceof Level.Infinity<LvlVar>) {
        avoidableEqns.append(e);
        return false;
      }
      return lhsLevels.anyMatch(left -> dealSingleLt(g, left, right));
    }
    specialEq.append(new Eqn(new Sort(lhsLevels), new Sort(rhsLevels.toImmutableSeq()), Ordering.Lt, e.sourcePos()));
    return false;
  }
}

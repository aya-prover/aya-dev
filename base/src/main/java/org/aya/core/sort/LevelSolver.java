// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.api.ref.LevelGenVar;
import org.aya.core.sort.Sort.LvlVar;
import org.aya.generic.Level;
import org.aya.util.Ordering;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.collection.mutable.MutableSet;
import org.jetbrains.annotations.NotNull;

import static org.aya.core.sort.Sort.constant;

/**
 * @author danihao123, ice1000
 */
public class LevelSolver {
  public static class UnsatException extends Exception {
  }

  static final int INF = 100000000;
  static final int INF_SMALL = INF / 10;
  static final int LOW_BOUND = 10000;
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
      if (d[0][u] < LOW_BOUND) return true;
      for (var nv : unfreeNodes) {
        int v = graphMap.get(nv);
        if (u != v && d[u][v] < LOW_BOUND) return true;
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

  MutableSet<LvlVar> unfreeNodes = MutableSet.of();
  MutableSet<LvlVar> freeNodes = MutableSet.of();
  MutableMap<LvlVar, Integer> graphMap = MutableMap.create();
  MutableMap<LvlVar, Integer> defaultValues = MutableMap.create();

  void genGraphNode(SeqLike<Level<LvlVar>> l) {
    for (var e : l) {
      if (e instanceof Level.Reference<LvlVar> th) {
        graphMap.put(th.ref(), ++nodeSize);
      }
    }
  }

  void dealSingleLt(int[][] g, Level<LvlVar> a, Level<LvlVar> b) throws UnsatException {
    if (b instanceof Level.Infinity) return;
    if (a instanceof Level.Infinity) {
      a = new Level.Constant<>(INF_SMALL);
    }
    if (a instanceof Level.Constant<LvlVar> ca) {
      if (b instanceof Level.Constant<LvlVar> cb) {
        if (ca.value() > cb.value()) {
          throw new UnsatException();
        }
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
  }

  void prepareGraphNode(int[][] g, SeqLike<Level<LvlVar>> l) {
    for (var e : l) {
      if (e instanceof Level.Reference<LvlVar> th) {
        int defaultValue = th.ref().kind().defaultValue - th.lift();
        int u = graphMap.get(th.ref());
        if (th.ref().free()) {
          // addEdge(g, u, 0, -defaultValue); // 认为自由变量一定大于等于其默认值（暂时取消这种想法）
          defaultValues.put(th.ref(), th.ref().kind().defaultValue);
          freeNodes.add(th.ref());
          // Universe level can't be inf, homotopy can
          if (th.ref().kind() == LevelGenVar.Kind.Universe) {
            addEdge(g, 0, u, LOW_BOUND);
          }
        } else {
          unfreeNodes.add(th.ref());
        }
      }
    }
  }

  int[][] dfs(SeqLike<LevelEqnSet.Eqn> l, int pos, int[][] g) throws UnsatException {
    if (pos >= l.size()) {
      if (floyd(g)) {
        throw new UnsatException();
      } else {
        return g;
      }
    }
    var th = l.get(pos);
    var lhsVar = th.lhs().levels();
    var rhsVar = th.rhs().levels();
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

  Level<LvlVar> resolveConstantLevel(int dist) {
    int retU;
    if (dist > LOW_BOUND) {
      retU = INF;
    } else {
      retU = dist;
    }
    if (retU >= INF) {
      return new Level.Infinity<>();
    } else {
      return constant(retU);
    }
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
    var specialEq = Buffer.<LevelEqnSet.Eqn>of();
    for (var e : equations) {
      var ord = e.cmp();
      var lhs = e.lhs();
      var rhs = e.rhs();
      if (ord == Ordering.Gt) {
        var temp = lhs;
        lhs = rhs;
        rhs = temp;
        ord = Ordering.Lt;
      }
      if (ord == Ordering.Lt) {
        var canBeAvoided = true;
        for (var v : lhs.levels()) {
          if (!rhs.levels().contains(v)) {
            canBeAvoided = false;
            break;
          }
        }
        if (canBeAvoided) continue;
        if (rhs.levels().size() == 1) {
          var right = rhs.levels().get(0);
          for (var left : lhs.levels()) {
            dealSingleLt(g, left, right);
          }
        } else {
          specialEq.append(e);
        }
      } else {
        specialEq.append(new LevelEqnSet.Eqn(lhs, rhs, Ordering.Lt, e.sourcePos()));
        specialEq.append(new LevelEqnSet.Eqn(rhs, lhs, Ordering.Lt, e.sourcePos()));
      }
    }
    if (floyd(g)) throw new UnsatException();
    var gg = dfs(specialEq, 0, g);
    for (var name : freeNodes) {
      int u = graphMap.get(name);
      int thDefault = defaultValues.get(name);
      int upperBound = gg[0][u];
      if (upperBound >= thDefault) {
        addEdge(gg, u, 0, thDefault);
      }
      int lowerBound = -gg[u][0];
      if (lowerBound < 0) lowerBound = 0;
      Buffer<Level<LvlVar>> upperNodes = Buffer.create();
      Buffer<Level<LvlVar>> lowerNodes = Buffer.create();
      for (var nu : unfreeNodes) {
        int v = graphMap.get(nu);
        if (gg[v][u] != INF) upperNodes.append(new Level.Reference<>(nu, gg[v][u]));
        if (gg[u][v] < LOW_BOUND / 2) lowerNodes.append(new Level.Reference<>(nu, -gg[u][v]));
      }
      Buffer<Level<LvlVar>> retList = Buffer.create();
      if (!lowerNodes.isEmpty() || upperNodes.isEmpty()) {
        if (lowerBound >= LOW_BOUND) {
          retList.append(new Level.Infinity<>());
        } else {
          if (lowerBound != 0 || lowerNodes.isEmpty()) retList.append(resolveConstantLevel(lowerBound));
          retList.appendAll(lowerNodes);
        }
      } else {
        int minv = upperBound;
        for (var _l : upperNodes) {
          if (_l instanceof Level.Reference<LvlVar> l) minv = Math.min(minv, l.lift());
        }
        retList.append(resolveConstantLevel(minv));
      }
      eqns.solution().put(name, new Sort.CoreLevel(retList.toImmutableSeq()));
    }
  }
}

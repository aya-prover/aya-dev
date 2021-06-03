// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.experiments;

import kala.collection.SeqLike;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;

import java.util.Scanner;


/**
 * @author danihao123
 */
public class ZzsSolver {
  static class UnsatException extends Exception {
  }

  interface Level {
  }

  record Const(int constant) implements Level {
  }

  record Infinity() implements Level {
  }

  record Reference(Var ref, int lift) implements Level {
  }

  // free means need to be 'solved'
  record Var(String name, boolean canBeInf, int defaultValue, boolean free) {
  }

  record Max(SeqLike<Level> levels) {
  }

  // <=, >=, ==
  enum Ord {Lt, Gt, Eq}

  record Equation(Ord ord, Max lhs, Max rhs) {
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
      if (d[0][u] != INF) return true;
      for (var nv : unfreeNodes) {
        int v = graphMap.get(nv);
        if (u != v && d[u][v] != INF) return true;
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

  MutableSet<String> unfreeNodes;
  MutableSet<String> freeNodes;
  MutableMap<String, Integer> graphMap;

  void genGraphNode(SeqLike<Level> l) {
    for (var e : l) {
      if (e instanceof Reference th) {
        graphMap.put(th.ref().name(), ++nodeSize);
      }
    }
  }

  void dealSingleLt(int[][] g, Level a, Level b) throws UnsatException {
    if (b instanceof Infinity) return;
    if (a instanceof Infinity) {
      a = new Const(INF_SMALL);
    }
    if (a instanceof Const ca) {
      if (b instanceof Const cb) {
        if (ca.constant() > cb.constant()) {
          throw new UnsatException();
        }
      } else if (b instanceof Reference rb) {
        // if(!rb.ref().free()) return;
        int u = ca.constant();
        int v = rb.lift();
        int x = graphMap.get(rb.ref().name());
        addEdge(g, x, 0, v - u);
      }
    } else if (a instanceof Reference ra) {
      // if(!ra.ref().free()) return;
      int x = graphMap.get(ra.ref().name());
      int u = ra.lift();
      if (b instanceof Const cb) {
        int v = cb.constant();
        addEdge(g, 0, x, v - u);
      } else if (b instanceof Reference rb) {
        // if(!rb.ref().free()) return;
        int y = graphMap.get(rb.ref().name());
        int v = rb.lift();
        addEdge(g, y, x, v - u);
      }
    }
  }

  void prepareGraphNode(int[][] g, SeqLike<Level> l) {
    for (var e : l) {
      if (e instanceof Reference th) {
        int defaultValue = th.ref().defaultValue() - th.lift();
        int u = graphMap.get(th.ref().name());
        if (th.ref().free()) {
          addEdge(g, u, 0, -defaultValue); // 认为自由变量一定大于等于其默认值
          freeNodes.add(th.ref().name());
          if (!th.ref().canBeInf()) {
            addEdge(g, 0, u, LOW_BOUND);
          }
        } else {
          unfreeNodes.add(th.ref().name());
        }
      }
    }
  }

  int[][] dfs(SeqLike<Equation> l, int pos, int[][] g) throws UnsatException {
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
    for (Level max : rhsVar) {
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
    return null;
  }

  Level resolveConstantLevel(int dist) {
    int retU;
    if (dist > LOW_BOUND) {
      retU = INF;
    } else {
      retU = dist;
    }
    if (retU >= INF) {
      return new Infinity();
    } else {
      return new Const(retU);
    }
  }

  MutableMap<String, Max> solve(SeqLike<Equation> equations) throws UnsatException {
    nodeSize = 0;
    graphMap = MutableMap.create();
    freeNodes = MutableSet.of();
    unfreeNodes = MutableSet.of();
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
    var specialEq = Buffer.<Equation>of();
    for (var e : equations) {
      var ord = e.ord();
      var lhs = e.lhs();
      var rhs = e.rhs();
      if (ord == Ord.Gt) {
        var temp = lhs;
        lhs = rhs;
        rhs = temp;
        ord = Ord.Lt;
      }
      if (ord == Ord.Lt) {
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
        specialEq.append(new Equation(Ord.Lt, rhs, lhs));
        specialEq.append(new Equation(Ord.Lt, lhs, rhs));
      }
    }
    if (floyd(g)) throw new UnsatException();
    var gg = dfs(specialEq, 0, g);
    var ret = MutableMap.<String, Max>of();
    for (var name : freeNodes) {
      int u = graphMap.get(name);
      int lowerBound = -gg[u][0];
      if (lowerBound < 0) lowerBound = 0;
      int upperBound = gg[0][u];
      Buffer<Level> upperNodes = Buffer.create();
      Buffer<Level> lowerNodes = Buffer.create();
      for (var nu : unfreeNodes) {
        int v = graphMap.get(nu);
        if (gg[v][u] != INF) {
          upperNodes.append(new Reference(new Var(nu, true, 0, false), gg[v][u]));
        }
        if (gg[u][v] != INF) {
          lowerNodes.append(new Reference(new Var(nu, true, 0, false), -gg[u][v]));
        }
      }
      Buffer<Level> retList = Buffer.create();
      if (!lowerNodes.isEmpty() || upperNodes.isEmpty()) {
        retList.append(resolveConstantLevel(lowerBound));
        retList.appendAll(lowerNodes);
      } else {
        int minv = upperBound;
        for (var _l : upperNodes) {
          if (_l instanceof Reference l) minv = Math.min(minv, l.lift());
        }
        retList.append(resolveConstantLevel(minv));
      }
      ret.put(name, new Max(retList));
    }
    return ret;
  }

  public static void main(String[] args) throws UnsatException {
    var in = new Scanner(System.in);
    int T = in.nextInt();
    Buffer<Equation> list = Buffer.create();
    for (int i = 1; i <= T; i++) {
      var x = in.next();
      int u = in.nextInt();
      var y = in.next();
      int v = in.nextInt();
      boolean freeX = true, freeY = true;
      if (x.endsWith("_")) freeX = false;
      if (y.endsWith("_")) freeY = false;
      Level var_1 = new Reference(new Var(x, true, 0, freeX), u);
      if (x.equals("0")) var_1 = new Const(u);
      Level var_2 = new Reference(new Var(y, true, 0, freeY), v);
      if (y.equals("0")) var_2 = new Const(v);
      Buffer<Level> lhs = Buffer.create();
      lhs.append(var_1);
      Buffer<Level> rhs = Buffer.of();
      rhs.append(var_2);
      list.append(new Equation(Ord.Lt, new Max(lhs), new Max(rhs)));
    }
    System.out.println(new ZzsSolver().solve(list));
  }
}

// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.experiments;

import java.util.*;


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
  record Var(String name, boolean free) {
  }

  record Max(List<Level> levels) {
  }

  // <=, >=, ==
  enum Ord {Lt, Gt, Eq}

  record Equation(Ord ord, Max lhs, Max rhs) {
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

  private final HashSet<String> unfreeNodes = new HashSet<>();
  private final HashSet<String> freeNodes = new HashSet<>();
  private final HashMap<String, Integer> graphMap = new HashMap<>();
  private final HashMap<String, Integer> defaultValues = new HashMap<>();
  public final List<Equation> avoidableEqns = new ArrayList<>();

  void genGraphNode(List<Level> l) {
    for (var e : l) {
      if (e instanceof Reference th) {
        graphMap.put(th.ref().name(), ++nodeSize);
      }
    }
  }

  private boolean dealSingleLt(int[][] g, Level a, Level b) {
    if (a instanceof Const ca) {
      if (b instanceof Const cb) {
        return ca.constant > cb.constant;
      } else if (b instanceof Reference rb) {
        // if(!rb.ref().free()) return;
        int u = ca.constant;
        int v = rb.lift();
        int x = graphMap.get(rb.ref().name);
        addEdge(g, x, 0, v - u);
      }
    } else if (a instanceof Reference ra) {
      // if(!ra.ref().free()) return;
      int x = graphMap.get(ra.ref().name);
      int u = ra.lift();
      if (b instanceof Const cb) {
        int v = cb.constant;
        addEdge(g, 0, x, v - u);
      } else if (b instanceof Reference rb) {
        // if(!rb.ref().free()) return;
        int y = graphMap.get(rb.ref().name());
        int v = rb.lift();
        addEdge(g, y, x, v - u);
      }
    }
    return false;
  }

  void prepareGraphNode(int[][] g, List<Level> l) {
    for (var e : l) {
      if (e instanceof Reference th) {
        int defaultValue = -th.lift();
        int u = graphMap.get(th.ref().name());
        if (th.ref().free()) {
          // addEdge(g, u, 0, -defaultValue); // 认为自由变量一定大于等于其默认值（暂时取消这种想法）
          defaultValues.put(th.ref().name(), 0);
          freeNodes.add(th.ref().name());
          addEdge(g, 0, u, LOW_BOUND);
        } else {
          unfreeNodes.add(th.ref().name());
        }
      }
    }
  }

  int[][] dfs(ArrayList<Equation> l, int pos, int[][] g) throws UnsatException {
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
    throw new UnsatException();
  }

  Map<String, Max> solve(List<Equation> equations) throws UnsatException {
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
    var specialEq = new ArrayList<Equation>();
    for (var e : equations) {
      var lhs = e.lhs();
      var rhs = e.rhs();
      switch (e.ord()) {
        case Lt -> populateLT(g, specialEq, e, lhs, rhs);
        case Gt -> populateLT(g, specialEq, e, rhs, lhs);
        case Eq -> {
          specialEq.add(new Equation(Ord.Lt, rhs, lhs));
          specialEq.add(new Equation(Ord.Lt, lhs, rhs));
        }
      }
    }
    if (floyd(g)) throw new UnsatException();
    var gg = dfs(specialEq, 0, g);
    var ret = new HashMap<String, Max>();
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
      List<Level> upperNodes = new ArrayList<>();
      List<Level> lowerNodes = new ArrayList<>();
      for (var nu : unfreeNodes) {
        int v = graphMap.get(nu);
        // 下面认为，非自由变量是否可以是无穷大、是否有默认值是无关紧要的
        if (gg[v][u] != INF) {
          upperNodes.add(new Reference(new Var(nu, false), gg[v][u]));
        }
        if (gg[u][v] < LOW_BOUND / 2) {
          lowerNodes.add(new Reference(new Var(nu, false), -gg[u][v]));
        }
      }
      List<Level> retList = new ArrayList<>();
      if (!lowerNodes.isEmpty() || upperNodes.isEmpty()) {
        if (lowerBound != 0 || lowerNodes.isEmpty()) retList.add(new Const(lowerBound));
        retList.addAll(lowerNodes);
      } else {
        int minv = upperBound;
        for (var _l : upperNodes) {
          if (_l instanceof Reference l) minv = Math.min(minv, l.lift());
        }
        retList.add(new Const(minv));
      }
      ret.put(name, new Max(retList));
    }
    return ret;
  }

  private void populateLT(int[][] g, ArrayList<Equation> specialEq, Equation e, Max lhs, Max rhs) {
    for (var v : lhs.levels()) {
      if (!rhs.levels().contains(v)) {
        avoidableEqns.add(e);
        break;
      }
    }
    if (rhs.levels().size() == 1) {
      var right = rhs.levels().get(0);
      if (right instanceof Infinity) {
        avoidableEqns.add(e);
        return;
      }
      for (var left : lhs.levels()) dealSingleLt(g, left, right);
    } else specialEq.add(e);
  }

  public static void main(String[] args) throws UnsatException {
    var res = new ZzsSolver().solve(List.of(new Equation(Ord.Lt, new Max(List.of(new Const(0))), new Max(List.of(new Reference(new Var("Semigroup.u", true), 0)))),
      // new Equation(Ord.Lt, new Max(List.of(new Const(0))), new Max(List.of(new Reference(new Var("Semigroup.u", true), 0)))),
      new Equation(Ord.Lt, new Max(List.of(new Const(0))), new Max(List.of(new Reference(new Var("u", false), 0)))),
      new Equation(Ord.Lt, new Max(List.of(new Reference(new Var("Semigroup.u", true), 0))), new Max(List.of(new Reference(new Var("Semigroup.u", true), 0))))));
    System.out.println(res);
  }
}

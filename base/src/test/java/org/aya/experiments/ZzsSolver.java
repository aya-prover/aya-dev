// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.experiments;

import java.util.*;
import java.util.stream.Collectors;


/**
 * @author danihao123
 */
public class ZzsSolver {
  static class UnsatException extends Exception {
  }

  interface Level {
  }

  record Const(int constant) implements Level {
    @Override public String toString() {
      return String.valueOf(constant);
    }
  }

  record Infinity() implements Level {
    @Override public String toString() {
      return "inf";
    }
  }

  record Reference(Var ref, int lift) implements Level {
    @Override public String toString() {
      return lift == 0 ? ref.toString() : "(" + ref + " + " + lift + ")";
    }
  }

  // free means need to be 'solved'
  record Var(String name, boolean free) {
    @Override public String toString() {
      return (free ? "_" : "") + name;
    }
  }

  record Max(List<Level> levels) {
    public Max(Level... levels) {
      this(Arrays.asList(levels));
    }

    @Override public String toString() {
      return levels.size() == 1 ? levels.get(0).toString()
        : "max " + levels.stream().map(Object::toString).collect(Collectors.joining(" "));
    }
  }

  // <=, >=, ==
  enum Ord {Lt, Gt, Eq}

  record Equation(Ord ord, Max lhs, Max rhs) {
    @Override public String toString() {
      return lhs + switch (ord) {
        case Lt -> " <= ";
        case Gt -> " >= ";
        case Eq -> " == ";
      } + rhs;
    }
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

  private final HashSet<Var> unfreeNodes = new HashSet<>();
  private final HashSet<Var> freeNodes = new HashSet<>();
  private final HashMap<Var, Integer> graphMap = new HashMap<>();
  private final HashMap<Var, Integer> defaultValues = new HashMap<>();
  public final List<Equation> avoidableEqns = new ArrayList<>();

  void genGraphNode(List<Level> l) {
    for (var e : l) {
      if (e instanceof Reference th) {
        graphMap.put(th.ref(), ++nodeSize);
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
        int x = graphMap.get(rb.ref());
        addEdge(g, x, 0, v - u);
      }
    } else if (a instanceof Reference ra) {
      // if(!ra.ref().free()) return;
      int x = graphMap.get(ra.ref());
      int u = ra.lift();
      if (b instanceof Const cb) {
        int v = cb.constant;
        addEdge(g, 0, x, v - u);
      } else if (b instanceof Reference rb) {
        // if(!rb.ref().free()) return;
        int y = graphMap.get(rb.ref());
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
        int u = graphMap.get(th.ref());
        if (th.ref().free()) {
          // addEdge(g, u, 0, -defaultValue); // 认为自由变量一定大于等于其默认值（暂时取消这种想法）
          defaultValues.put(th.ref(), 0);
          freeNodes.add(th.ref());
          addEdge(g, 0, u, LOW_BOUND);
        } else {
          unfreeNodes.add(th.ref());
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

  Map<String, Max> solve(List<Equation> equations) throws UnsatException {
    System.out.println("Equations:");
    for (var equation : equations) System.out.println(equation);
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
        case Lt -> populateLT(g, specialEq, e, lhs, rhs, true);
        case Gt -> populateLT(g, specialEq, e, rhs, lhs, true);
        case Eq -> {
          populateLT(g, specialEq, e, lhs, rhs, true);
          populateLT(g, specialEq, e, rhs, lhs, true);
        }
      }
    }
    if (floyd(g)) throw new UnsatException();

    for (var e : equations) {
      var lhs = e.lhs();
      var rhs = e.rhs();
      switch (e.ord()) {
        case Lt -> populateLT(g, specialEq, e, lhs, rhs, false);
        case Gt -> populateLT(g, specialEq, e, rhs, lhs, false);
        case Eq -> {
          populateLT(g, specialEq, e, lhs, rhs, false);
          populateLT(g, specialEq, e, rhs, lhs, false);
        }
      }
    }
    if (!avoidableEqns.isEmpty()) System.out.println("Avoided:");
    for (var equation : avoidableEqns) System.out.println(equation);
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
          upperNodes.add(new Reference(new Var(nu.name, false), gg[v][u]));
        }
        if (gg[u][v] < LOW_BOUND / 2) {
          lowerNodes.add(new Reference(new Var(nu.name, false), -gg[u][v]));
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
      ret.put(name.name, new Max(retList));
    }
    return ret;
  }

  private void populateLT(int[][] g, ArrayList<Equation> specialEq, Equation e, Max lhs, Max rhs, boolean complex) {
    if (complex && rhs.levels.size() > 1) return;
    var lhsLevels = new ArrayList<Level>();
    var rhsLevels = new ArrayList<Level>();
    for (var vr : lhs.levels()) {
      boolean toInsert = true;
      if (vr instanceof Reference ref) {
        var th = ref.ref();
        for (var vp : rhs.levels()) {
          if (vp instanceof Reference __r) {
            var tp = __r.ref();
            if (g[graphMap.get(tp)][graphMap.get(th)] + ref.lift() - __r.lift() <= 0) {
              toInsert = false;
            }
          }
        }
      }
      if (toInsert) lhsLevels.add(vr);
    }
    for (var vr : rhs.levels()) {
      boolean toInsert = true;
      if (vr instanceof Reference ref) {
        var th = ref.ref();
        if (!th.free()) {
          toInsert = false;
          lhsLevels.forEach(left -> dealSingleLt(g, left, vr));
        }
      }
      if (toInsert) rhsLevels.add(vr);
    }
    if (lhsLevels.isEmpty() || rhsLevels.isEmpty()) return;
    if (lhsLevels.size() == 1 && rhsLevels.size() > 1) {
      var left = lhsLevels.get(0);
      if (left instanceof Const constant && constant.constant == 0) {
        avoidableEqns.add(e);
        return;
      }
    }
    if (rhsLevels.size() == 1) {
      var right = rhsLevels.get(0);
      if (right instanceof Infinity) {
        avoidableEqns.add(e);
        return;
      }
      lhsLevels.forEach(left -> dealSingleLt(g, left, right));
      return;
    }
    specialEq.add(new Equation(Ord.Lt, new Max(lhsLevels), new Max(rhsLevels)));
  }

  public static void main(String[] args) throws UnsatException {
    var res = new ZzsSolver().solve(List.of(new Equation(Ord.Eq, new Max(List.of(new Reference(new Var("Quantifier.u", true), 0))), new Max(List.of(new Reference(new Var("u", false), 0)))),
      new Equation(Ord.Eq, new Max(List.of(new Reference(new Var("Quantifier.v", true), 0))), new Max(List.of(new Reference(new Var("v", false), 0)))),
      new Equation(Ord.Eq, new Max(List.of(new Reference(new Var("Event.u", true), 0))), new Max(List.of(new Reference(new Var("u", false), 0)))),
      new Equation(Ord.Lt, new Max(List.of(new Reference(new Var("u", false), 0))), new Max(List.of(new Reference(new Var("Event.u", true), 0)))),
      new Equation(Ord.Lt, new Max(List.of(new Reference(new Var("Event.u", true), 0))), new Max(List.of(new Reference(new Var("Quantifier.u", true), 0)))),
      new Equation(Ord.Lt, new Max(List.of(new Reference(new Var("Quantifier.u", true), 0), new Reference(new Var("Quantifier.v", true), 1))), new Max(List.of(new Reference(new Var("u", false), 0), new Reference(new Var("v", false), 1))))));
    System.out.println(res);
  }
}

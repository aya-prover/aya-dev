// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

public record UnionFind(int[] parent) {
  public UnionFind(int size) {
    this(new int[size]);
    for (int i = 0; i < size; i++) parent[i] = i;
  }

  public int find(int i) {
    int root = i;
    while (root != parent[root]) {
      root = parent[root];
    }
    int curr = i;
    while (curr != root) {
      int nxt = parent[curr];
      parent[curr] = root;
      curr = nxt;
    }
    return root;
  }

  public void union(int i, int j) {
    int rootI = find(i);
    int rootJ = find(j);
    if (rootI != rootJ) {
      parent[rootI] = rootJ;
    }
  }
}

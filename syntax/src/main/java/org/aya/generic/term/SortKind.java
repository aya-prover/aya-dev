// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic.term;

/**
 * In fact, we will barely use <code>Set</code> or <code>ISet</code>.
 * 99.114514% of the time, we will use <code>Type</code>.
 */
public enum SortKind {
  Type, Set, ISet;
  public boolean hasLevel() { return this == Type || this == Set; }
}

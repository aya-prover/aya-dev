// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

public enum SortKind {
  Type, Set, ISet;

  public boolean hasLevel() {
    return this == Type || this == Set;
  }
}

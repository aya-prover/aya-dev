// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import org.aya.generic.Constants;
import org.jetbrains.annotations.NotNull;

/**
 * Thread-unsafe name generator
 */
public class NameGenerator {
  private int id = 0;
  public int nextId() { return id++; }
  public @NotNull String nextName() { return Constants.ANONYMOUS_PREFIX + nextId(); }
}

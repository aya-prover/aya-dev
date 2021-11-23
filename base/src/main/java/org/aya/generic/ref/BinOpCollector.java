// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic.ref;

import org.aya.api.ref.Var;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * I'm sorry. I don't know any better way to do this.
 * @author ice1000
 */
public interface BinOpCollector {
  @NotNull Set<Var> COLLECTION = Collections.newSetFromMap(new WeakHashMap<>());

  static boolean isInfix(@NotNull Var var) {
    return COLLECTION.contains(var);
  }

  static void collect(@NotNull Var var) {
    COLLECTION.add(var);
  }
}

// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.states;

import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.syntax.core.def.ClassDefLike;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MapLocalCtx;
import org.jetbrains.annotations.NotNull;

/// Local instance set.
/// Mutable during the typechecking of a single declaration.
///
/// @see GlobalInstanceSet
public class InstanceSet {
  public final @NotNull GlobalInstanceSet parent;
  /// The type of which is stored in a [org.aya.syntax.ref.LocalCtx]
  private final @NotNull MutableMap<ClassDefLike, MutableList<LocalVar>> instanceMap = MutableMap.create();
  private final @NotNull MapLocalCtx instanceTypes = new MapLocalCtx();

  public InstanceSet(@NotNull GlobalInstanceSet parent) {
    this.parent = parent;
  }

  public void put(@NotNull LocalVar instance, @NotNull ClassCall type) {
    instanceMap.getOrPut(type.ref(), MutableList::create).append(instance);
    instanceTypes.put(instance, type);
  }
}

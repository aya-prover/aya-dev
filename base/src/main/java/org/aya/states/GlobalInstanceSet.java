// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.states;

import kala.collection.Seq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.syntax.core.def.ClassDefLike;
import org.aya.syntax.core.def.FnDefLike;
import org.jetbrains.annotations.NotNull;

/// Declaration-level instance set.
/// Should be immutable during the typechecking of a single declaration.
///
/// @see InstanceSet
public class GlobalInstanceSet {
  private final @NotNull MutableMap<ClassDefLike, MutableList<FnDefLike>> instanceMap = MutableMap.create();

  public void put(@NotNull ClassDefLike clazz, @NotNull FnDefLike instance) {
    instanceMap.getOrPut(clazz, MutableList::create).append(instance);
  }

  @NotNull Seq<FnDefLike> findInstanceDecls(@NotNull ClassDefLike clazz) {
    return instanceMap.getOrPut(clazz, MutableList::create);
  }
}

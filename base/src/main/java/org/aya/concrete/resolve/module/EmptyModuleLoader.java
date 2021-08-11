// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.module;

import kala.collection.Seq;
import kala.collection.mutable.MutableMap;
import org.aya.api.ref.Var;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmptyModuleLoader implements ModuleLoader {
  @Override
  public @Nullable MutableMap<Seq<String>, MutableMap<String, Var>> load(@NotNull Seq<@NotNull String> path, @NotNull ModuleLoader recurseLoader) {
    return null;
  }
}

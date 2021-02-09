// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.module;

import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;

public class EmptyModuleLoader implements ModuleLoader {
  @Override
  public @Nullable MutableMap<Seq<String>, MutableMap<String, Var>> load(@NotNull Seq<@NotNull String> path) {
    return null;
  }
}

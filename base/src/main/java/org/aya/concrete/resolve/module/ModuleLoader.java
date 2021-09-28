// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.api.ref.Var;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public interface ModuleLoader {
  @Nullable MutableMap<ImmutableSeq<String>, MutableMap<String, Var>>
  load(@NotNull ImmutableSeq<@NotNull String> path, @NotNull ModuleLoader recurseLoader);

  default @Nullable MutableMap<ImmutableSeq<String>, MutableMap<String, Var>>
  load(@NotNull ImmutableSeq<@NotNull String> path) {
    return load(path, this);
  }
}

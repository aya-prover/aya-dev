// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.resolve.ResolveInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public interface ModuleLoader {
  @Nullable ResolveInfo load(@NotNull ImmutableSeq<@NotNull String> path, @NotNull ModuleLoader recurseLoader);

  default @Nullable ResolveInfo load(@NotNull ImmutableSeq<@NotNull String> path) {
    return load(path, this);
  }
}

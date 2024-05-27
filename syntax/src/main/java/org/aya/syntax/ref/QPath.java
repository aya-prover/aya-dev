// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import org.jetbrains.annotations.NotNull;

public record QPath(@NotNull ModulePath module, int fileModuleSize) {
  public QPath {
    assert 0 < fileModuleSize && fileModuleSize <= module.module().size();
  }

  public @NotNull ModulePath fileModule() {
    return new ModulePath(module.module().take(fileModuleSize));
  }
}

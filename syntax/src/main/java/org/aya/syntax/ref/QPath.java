// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.function.Function;

/**
 * A qualified path to some module
 */
public record QPath(@NotNull ModulePath module, int fileModuleSize) implements Serializable {
  public static @NotNull QPath fileLevel(@NotNull ModulePath fileLevelModule) {
    return new QPath(fileLevelModule, fileLevelModule.size());
  }

  public QPath {
    assert 0 < fileModuleSize && fileModuleSize <= module.module().size() : "fileModuleSize = " + fileModuleSize;
  }

  public @NotNull ModulePath fileModule() {
    return new ModulePath(module.module().take(fileModuleSize));
  }

  public @NotNull ModuleName localModule() {
    var dropped = module.module().drop(fileModuleSize);
    if (dropped.isEmpty()) return ModuleName.This;
    return new ModuleName.Qualified(dropped);
  }

  public @NotNull QPath derive(@NotNull String name) {
    return new QPath(module.derive(name), fileModuleSize);
  }

  public @NotNull QPath derive(@NotNull ModulePath name) {
    return new QPath(module.derive(name), fileModuleSize);
  }

  public @NotNull QPath derive(@NotNull ModuleName.Qualified name) {
    return new QPath(module.derive(new ModulePath(name.ids())), fileModuleSize);
  }

  public boolean isFileModule() {
    return module.size() == fileModuleSize;
  }

  /**
   * Iterate a {@link ModulePath} in file tree way, starts from {@link #fileModule}
   */
  public <R> @NotNull ImmutableSeq<R> traversal(Function<QPath, R> tabibito) {
    var result = MutableList.<R>create();

    for (int i = fileModuleSize; i <= module.size(); ++i) {
      result.append(tabibito.apply(new QPath(new ModulePath(module.module().take(i)), fileModuleSize)));
    }

    return result.toSeq();
  }

  @Override
  public @NotNull String toString() {
    return module.toString();
  }
}

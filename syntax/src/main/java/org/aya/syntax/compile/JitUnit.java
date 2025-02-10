// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import kala.collection.immutable.ImmutableArray;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.ref.QName;
import org.aya.syntax.ref.QPath;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

public abstract class JitUnit {
  private CompiledAya metadata;

  public @NotNull final CompiledAya metadata() {
    if (metadata == null) metadata = getClass().getAnnotation(CompiledAya.class);
    if (metadata == null) throw new Panic("No @CompiledAya on " + getClass().getName());
    return metadata;
  }

  public @NotNull String name() { return metadata().name(); }
  public @NotNull ModulePath module() { return new ModulePath(rawModule()); }

  private @NotNull ImmutableArray<String> rawModule() {
    return ImmutableArray.Unsafe.wrap(metadata().module());
  }

  /**
   * @implNote use {@link #metadata} after {@link #metadata()} being called is safe,
   * because {@link #metadata} is initialized in {@link #metadata()}.
   */
  public @NotNull QName qualifiedName() {
    var module = module();
    return new QName(new QPath(module, metadata.fileModuleSize()), name());
  }

  public @NotNull ModulePath fileModule() {
    return new ModulePath(rawModule().take(metadata().fileModuleSize()));
  }
}

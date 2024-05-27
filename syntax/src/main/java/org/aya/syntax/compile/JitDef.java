// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import kala.collection.immutable.ImmutableArray;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.ref.QName;
import org.aya.syntax.ref.QPath;
import org.aya.util.binop.Assoc;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A well-typed compiled definition
 *
 * @implNote every definition should be annotated by {@link CompiledAya}
 */
public abstract sealed class JitDef extends JitTele implements AnyDef permits JitCon, JitData, JitFn {
  private CompiledAya metadata;

  protected JitDef(int telescopeSize, boolean[] telescopeLicit, String[] telescopeNames) {
    super(telescopeSize, telescopeLicit, telescopeNames);
  }

  public @NotNull CompiledAya metadata() {
    if (metadata == null) metadata = getClass().getAnnotation(CompiledAya.class);
    if (metadata == null) throw new Panic(STR."No @CompiledAya on \{getClass().getName()}");
    return metadata;
  }

  @Override public @NotNull ModulePath fileModule() {
    return new ModulePath(module().module().take(metadata.fileModuleSize()));
  }

  @Override public @NotNull ModulePath module() {
    return new ModulePath(ImmutableArray.Unsafe.wrap(metadata().module()));
  }
  /**
   * @implNote use {@link #metadata} after {@link #metadata()} being called is safe,
   * because {@link #metadata} is initialized in {@link #metadata()}.
   */
  @Override public @NotNull QName qualifiedName() {
    var module = module();
    return new QName(new QPath(module, metadata.fileModuleSize()), metadata.name());
  }
  @Override public @NotNull String name() { return metadata().name(); }
  @Override public @Nullable Assoc assoc() {
    var idx = metadata().assoc();
    if (idx == -1) return null;
    return Assoc.values()[idx];
  }

  @Override public @Nullable OpInfo opInfo() {
    var assoc = assoc();
    if (assoc == null) return null;
    return new OpInfo(name(), assoc);
  }
}

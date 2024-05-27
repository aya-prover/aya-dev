// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.NameGenerator;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.core.def.*;
import org.aya.util.IterableUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Serializing a module, note that it may not a file module, so we need not to make importing.
 */
public final class ModuleSerializer extends AbstractSerializer<ModuleSerializer.ModuleResult> {
  public record ModuleResult(
    @NotNull String name,
    @NotNull ImmutableSeq<TopLevelDef> defs,
    @NotNull ImmutableSeq<ModuleResult> submodules
  ) { }

  private final @NotNull ShapeFactory shapeFactory;

  public ModuleSerializer(@NotNull StringBuilder builder, int indent, @NotNull NameGenerator nameGen, @NotNull ShapeFactory shapeFactory) {
    super(builder, indent, nameGen);
    this.shapeFactory = shapeFactory;
  }

  public ModuleSerializer(@NotNull AbstractSerializer<?> other, @NotNull ShapeFactory shapeFactory) {
    super(other);
    this.shapeFactory = shapeFactory;
  }

  private void serializeCons(@NotNull DataDef dataDef, @NotNull DataSerializer serializer) {
    var ser = new ConSerializer(serializer);
    IterableUtil.forEach(dataDef.body, ser::appendLine, ser::serialize);
  }

  private void doSerialize(@NotNull TyckDef unit) {
    switch (unit) {
      case FnDef teleDef -> new FnSerializer(this)
        .serialize(teleDef);
      case DataDef dataDef -> new DataSerializer(this, shapeFactory, ser -> serializeCons(dataDef, ser))
        .serialize(dataDef);
      case ConDef conDef -> new ConSerializer(builder, indent, nameGen)
        .serialize(conDef);
      case PrimDef primDef -> throw new UnsupportedOperationException("TODO");
    }
  }

  private void doSerialize(ModuleResult unit, boolean isTopLevel) {
    var moduleName = javify(unit.name);

    buildClass(moduleName, null, !isTopLevel, () -> {
      IterableUtil.forEach(unit.defs, this::appendLine, this::doSerialize);
      // serialize submodules
      if (unit.submodules.isNotEmpty()) appendLine();
      IterableUtil.forEach(unit.submodules, this::appendLine, r -> doSerialize(r, false));
    });
  }

  @Override public AyaSerializer<ModuleResult> serialize(ModuleResult unit) {
    doSerialize(unit, true);

    return this;
  }
}

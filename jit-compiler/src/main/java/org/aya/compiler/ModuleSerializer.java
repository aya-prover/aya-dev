// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.FreeClassBuilder;
import org.aya.compiler.free.FreeJavaBuilder;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.core.def.*;
import org.aya.syntax.ref.QPath;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

import static org.aya.compiler.NameSerializer.getReference;

/**
 * Serializing a module, note that it may not a file module, so we need not to make importing.
 */
public final class ModuleSerializer<Carrier> {
  public record ModuleResult(
    @NotNull QPath name,
    @NotNull ImmutableSeq<TopLevelDef> defs
  ) { }

  private final @NotNull ShapeFactory shapeFactory;

  public ModuleSerializer(@NotNull ShapeFactory shapeFactory) {
    this.shapeFactory = shapeFactory;
  }

  private void serializeCons(@NotNull FreeClassBuilder builder, @NotNull DataDef dataDef) {
    var ser = new ConSerializer();
    dataDef.body.forEach(con -> ser.serialize(builder, con));
  }

  private void serializeMems(@NotNull FreeClassBuilder builder, @NotNull ClassDef classDef) {
    var ser = new MemberSerializer();
    classDef.members().forEach(mem -> ser.serialize(builder, mem));
  }

  private void doSerialize(@NotNull FreeClassBuilder builder, @NotNull TyckDef unit) {
    switch (unit) {
      case FnDef teleDef -> new FnSerializer(shapeFactory)
        .serialize(builder, teleDef);
      case DataDef dataDef -> {
        new DataSerializer(shapeFactory).serialize(builder, dataDef);
        serializeCons(builder, dataDef);
      }
      case ConDef conDef -> new ConSerializer()
        .serialize(builder, conDef);
      case PrimDef primDef -> new PrimSerializer()
        .serialize(builder, primDef);
      case ClassDef classDef -> {
        new ClassSerializer()
          .serialize(builder, classDef);
        serializeMems(builder, classDef);
      }
      case MemberDef memberDef -> new MemberSerializer()
        .serialize(builder, memberDef);
    }
  }

  public Carrier serialize(@NotNull FreeJavaBuilder<Carrier> builder, ModuleResult unit) {
    var desc = ClassDesc.of(getReference(unit.name, null, NameSerializer.NameType.ClassName));

    return builder.buildClass(desc, Object.class, cb -> {
      unit.defs.forEach(def -> doSerialize(cb, def));
    });
  }
}

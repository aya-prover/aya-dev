// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import java.lang.constant.ClassDesc;

import static org.aya.compiler.serializers.NameSerializer.getReference;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.compiler.AsmOutputCollector;
import org.aya.compiler.free.FreeClassBuilder;
import org.aya.compiler.free.FreeJavaBuilder;
import org.aya.compiler.free.morphism.asm.AsmJavaBuilder;
import org.aya.compiler.free.morphism.free.FreeJavaBuilderImpl;
import org.aya.compiler.free.morphism.free.FreeOptimizer;
import org.aya.compiler.free.morphism.free.FreeRunner;
import org.aya.compiler.serializers.MatchySerializer.MatchyData;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.compile.JitUnit;
import org.aya.syntax.core.def.*;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.syntax.ref.QPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Serializing a module, note that it may not a file module, so we need not to make importing.
 */
public final class ModuleSerializer {
  /** Input to the module serializer. */
  public record ModuleResult(
    @NotNull QPath name,
    @NotNull ImmutableSeq<TopLevelDef> defs
  ) { }

  public static class MatchyRecorder {
    public final @NotNull MutableList<MatchyData> todoMatchies = MutableList.create();
    public void addMatchy(Matchy clauses, int argsSize, int captureSize) {
      todoMatchies.append(new MatchyData(clauses, argsSize, captureSize));
    }
  }

  private final @NotNull ShapeFactory shapeFactory;
  private final @NotNull MatchyRecorder recorder = new MatchyRecorder();

  public ModuleSerializer(@NotNull ShapeFactory shapeFactory) {
    this.shapeFactory = shapeFactory;
  }

  private void serializeCons(@NotNull FreeClassBuilder builder, @NotNull DataDef dataDef) {
    var ser = new ConSerializer(recorder);
    dataDef.body().forEach(con -> ser.serialize(builder, con));
  }

  private void serializeMems(@NotNull FreeClassBuilder builder, @NotNull ClassDef classDef) {
    var ser = new MemberSerializer(recorder);
    classDef.members().forEach(mem -> ser.serialize(builder, mem));
  }

  private void doSerialize(@NotNull FreeClassBuilder builder, @NotNull TyckDef unit) {
    switch (unit) {
      case FnDef teleDef -> new FnSerializer(shapeFactory, recorder)
        .serialize(builder, teleDef);
      case DataDef dataDef -> {
        new DataSerializer(shapeFactory, recorder).serialize(builder, dataDef);
        serializeCons(builder, dataDef);
      }
      case ConDef conDef -> new ConSerializer(recorder)
        .serialize(builder, conDef);
      case PrimDef primDef -> new PrimSerializer(recorder)
        .serialize(builder, primDef);
      case ClassDef classDef -> {
        new ClassSerializer(recorder)
          .serialize(builder, classDef);
        serializeMems(builder, classDef);
      }
      case MemberDef memberDef -> new MemberSerializer(recorder)
        .serialize(builder, memberDef);
    }
  }

  public @NotNull AsmOutputCollector.Default serializeWithBestBuilder(ModuleResult unit) {
    var freeJava = serialize(FreeJavaBuilderImpl.INSTANCE, unit);
    freeJava = FreeOptimizer.optimizeClass(freeJava);
    return new FreeRunner<>(new AsmJavaBuilder<>(new AsmOutputCollector.Default())).runFree(freeJava);
  }

  @VisibleForTesting
  public <Carrier> Carrier serialize(@NotNull FreeJavaBuilder<Carrier> builder, ModuleResult unit) {
    var desc = ClassDesc.of(getReference(unit.name, null, NameSerializer.NameType.ClassName));
    var metadata = new ClassTargetSerializer.CompiledAyaImpl(unit.name,
      "", -1, -1, new CodeShape.GlobalId[0]);

    return builder.buildClass(metadata, desc, JitUnit.class, cb -> {
      unit.defs.forEach(def -> doSerialize(cb, def));
      var matchySerializer = new MatchySerializer(recorder);
      while (recorder.todoMatchies.isNotEmpty()) matchySerializer
        .serialize(cb, recorder.todoMatchies.removeLast());
    });
  }
}

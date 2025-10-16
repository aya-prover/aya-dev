// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.compiler.AsmOutputCollector;
import org.aya.compiler.morphism.asm.AsmJavaBuilder;
import org.aya.compiler.morphism.ast.AstClassBuilder;
import org.aya.compiler.morphism.ast.AstOptimizer;
import org.aya.compiler.morphism.ast.AstRunner;
import org.aya.compiler.serializers.MatchySerializer.MatchyData;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.compile.JitUnit;
import org.aya.syntax.core.def.*;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.syntax.ref.QPath;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

import static org.aya.compiler.serializers.NameSerializer.getReference;

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

  private void serializeCons(@NotNull AstClassBuilder builder, @NotNull DataDef dataDef) {
    var ser = new ConSerializer(recorder);
    dataDef.body().forEach(con -> ser.serialize(builder, con));
  }

  private void serializeMems(@NotNull AstClassBuilder builder, @NotNull ClassDef classDef) {
    var ser = new MemberSerializer(recorder);
    classDef.members().forEach(mem -> ser.serialize(builder, mem));
  }

  private void doSerialize(@NotNull AstClassBuilder builder, @NotNull TyckDef unit) {
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

  public @NotNull AsmOutputCollector.Default serialize(ModuleResult unit) {
    var desc = ClassDesc.of(getReference(unit.name, null, NameSerializer.NameType.ClassName));
    var metadata = new ClassTargetSerializer.AyaMetadataImpl(unit.name,
      "", -1, -1, new CodeShape.GlobalId[0]);

    var classBuilder = new AstClassBuilder(metadata, desc, null, JitUnit.class);
    unit.defs.forEach(def -> doSerialize(classBuilder, def));
    var matchySerializer = new MatchySerializer(recorder);
    while (recorder.todoMatchies.isNotEmpty()) matchySerializer
      .serialize(classBuilder, recorder.todoMatchies.removeLast());
    var freeJava = classBuilder.build();
    freeJava = AstOptimizer.optimizeClass(freeJava);
    return new AstRunner<>(new AsmJavaBuilder<>(new AsmOutputCollector.Default())).runFree(freeJava);
  }
}

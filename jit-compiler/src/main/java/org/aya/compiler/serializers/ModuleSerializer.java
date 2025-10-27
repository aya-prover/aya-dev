// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.compiler.AsmOutputCollector;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.asm.AsmJavaBuilder;
import org.aya.compiler.morphism.ast.AstClassBuilder;
import org.aya.compiler.morphism.ast.AstRunner;
import org.aya.compiler.morphism.ast.BlockSimplifier;
import org.aya.compiler.serializers.MatchySerializer.MatchyData;
import org.aya.states.primitive.ShapeFactory;
import org.aya.syntax.compile.JitUnit;
import org.aya.syntax.core.def.*;
import org.aya.syntax.ref.QPath;
import org.glavo.classfile.ClassHierarchyResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.lang.constant.ClassDesc;

import static org.aya.compiler.serializers.NameSerializer.getReference;

/**
 * Serializing a module, note that it may not be a file module, so we need not make imports.
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
    var classBuilder = serializeToANF(unit);
    var freeJava = classBuilder.build();
    freeJava = BlockSimplifier.optimizeClass(freeJava);
    var usedClasses = classBuilder.usedClasses();
    var systemResolver = ClassHierarchyResolver.defaultResolver();
    return new AstRunner<>(new AsmJavaBuilder<>(new AsmOutputCollector.Default())).interpClass(freeJava,
      classDesc -> usedClasses.getOrElse(classDesc, () ->
        systemResolver.getClassInfo(classDesc)));
  }

  @VisibleForTesting
  public @NotNull AstClassBuilder serializeToANF(ModuleResult unit) {
    var desc = ClassDesc.of(getReference(unit.name, null, NameSerializer.NameType.ClassName));
    var metadata = new ClassTargetSerializer.AyaMetadataImpl(unit.name, "");

    var classMarkers = MutableMap.of(
      Constants.CD_ImmutableSeq, ClassHierarchyResolver.ClassHierarchyInfo.ofInterface(),
      Constants.CD_ConCallLike, ClassHierarchyResolver.ClassHierarchyInfo.ofInterface()
    );
    var classBuilder = new AstClassBuilder(metadata, desc, null, classMarkers, JitUnit.class);
    unit.defs.forEach(def -> doSerialize(classBuilder, def));
    var matchySerializer = new MatchySerializer(recorder);
    while (recorder.todoMatchies.isNotEmpty()) matchySerializer
      .serialize(classBuilder, recorder.todoMatchies.removeLast());
    return classBuilder;
  }
}

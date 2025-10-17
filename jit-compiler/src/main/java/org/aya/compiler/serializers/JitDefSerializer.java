// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.morphism.JavaUtil;
import org.aya.compiler.morphism.ast.AstClassBuilder;
import org.aya.compiler.morphism.ast.AstCodeBuilder;
import org.aya.compiler.morphism.ast.AstVariable;
import org.aya.compiler.serializers.ModuleSerializer.MatchyRecorder;
import org.aya.syntax.compile.AyaMetadata;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.repr.CodeShape;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public abstract class JitDefSerializer<T extends TyckDef> extends ClassTargetSerializer<T> {
  protected JitDefSerializer(@NotNull Class<?> superClass, @NotNull MatchyRecorder recorder) {
    super(superClass, recorder);
  }

  /**
   * @see AyaMetadata
   */
  @Override protected @NotNull AyaMetadata buildMetadata(@NotNull T unit) {
    var ref = unit.ref();
    var module = ref.module;
    var assoc = ref.assoc();
    var assocIdx = assoc == null ? -1 : assoc.ordinal();
    assert module != null;
    return new AyaMetadataImpl(
      module, ref.name(),
      assocIdx,
      buildShape(unit),
      buildRecognition(unit)
    );
  }

  protected int buildShape(T unit) { return -1; }
  protected CodeShape.GlobalId[] buildRecognition(T unit) { return new CodeShape.GlobalId[0]; }
  protected abstract boolean shouldBuildEmptyCall(@NotNull T unit);
  /// Used in class instantiations
  protected abstract @NotNull Class<?> callClass();
  /// Used in type decls
  protected @NotNull Class<?> callBaseClass() { return callClass(); }

  protected final AstVariable buildEmptyCall(@NotNull AstCodeBuilder builder, @NotNull TyckDef def) {
    return builder.mkNew(callClass(), ImmutableSeq.of(AbstractExprializer.getInstance(builder, def)));
  }

  @Override protected @NotNull String className(T unit) {
    return NameSerializer.javifyClassName(unit.ref());
  }

  protected void buildFramework(@NotNull AstClassBuilder builder, @NotNull T unit, @NotNull Consumer<AstClassBuilder> continuation) {
    super.buildFramework(builder, unit, nestBuilder -> {
      if (shouldBuildEmptyCall(unit)) {
        nestBuilder.buildField(JavaUtil.fromClass(callBaseClass()),
          AyaSerializer.FIELD_EMPTYCALL, cb ->
            buildEmptyCall(cb, unit));
      }

      continuation.accept(nestBuilder);
    });
  }

  @Override
  public abstract @NotNull JitDefSerializer<T> serialize(@NotNull AstClassBuilder builder, T unit);
}

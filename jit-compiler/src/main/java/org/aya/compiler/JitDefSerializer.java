// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.FieldRef;
import org.aya.syntax.compile.CompiledAya;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.repr.CodeShape;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

import static org.aya.compiler.AyaSerializer.FIELD_EMPTYCALL;
import static org.aya.compiler.AyaSerializer.STATIC_FIELD_INSTANCE;
import static org.aya.compiler.NameSerializer.javifyClassName;

public abstract class JitDefSerializer<T extends TyckDef> {
  protected final @NotNull Class<?> superClass;

  protected JitDefSerializer(@NotNull Class<?> superClass) {
    this.superClass = superClass;
  }

  private static @NotNull CompiledAya mkCompiledAya(
    @NotNull String[] module,
    int fileModuleSize,
    @NotNull String name,
    int assoc,
    int shape,
    @NotNull CodeShape.GlobalId[] recognition
  ) {
    return new CompiledAya() {
      @Override public Class<? extends Annotation> annotationType() { return CompiledAya.class; }
      @Override public @NotNull String[] module() { return module; }
      @Override public int fileModuleSize() { return fileModuleSize; }
      @Override public @NotNull String name() { return name; }
      @Override public int assoc() { return assoc; }
      @Override public int shape() { return shape; }
      @Override public @NotNull CodeShape.GlobalId[] recognition() { return recognition; }
    };
  }

  /**
   * @see CompiledAya
   */
  protected @NotNull CompiledAya buildMetadata(@NotNull T unit) {
    var ref = unit.ref();
    var module = ref.module;
    var assoc = ref.assoc();
    var assocIdx = assoc == null ? -1 : assoc.ordinal();
    assert module != null;
    return mkCompiledAya(
      module.module().module().toArray(String.class),
      module.fileModuleSize(),
      ref.name(),
      assocIdx,
      buildShape(unit),
      buildRecognition(unit)
    );
  }

  protected int buildShape(T unit) { return -1; }
  protected CodeShape.GlobalId[] buildRecognition(T unit) { return new CodeShape.GlobalId[0]; }

  protected @NotNull FieldRef buildInstance(@NotNull FreeClassBuilder builder, @NotNull ClassDesc className) {
    return builder.buildConstantField(className, STATIC_FIELD_INSTANCE, b ->
      b.mkNew(className, ImmutableSeq.empty()));
  }

  protected abstract boolean shouldBuildEmptyCall(@NotNull T unit);

  protected abstract @NotNull Class<?> callClass();

  protected abstract void buildConstructor(@NotNull FreeClassBuilder builder, T unit);

  protected final FreeJavaExpr buildEmptyCall(@NotNull FreeExprBuilder builder, @NotNull AnyDef def) {
    return builder.mkNew(callClass(), ImmutableSeq.of(AbstractExprializer.getInstance(builder, def)));
  }

  protected void buildFramework(@NotNull FreeClassBuilder builder, @NotNull T unit, @NotNull Consumer<FreeClassBuilder> continuation) {
    var className = javifyClassName(unit.ref());
    var metadata = buildMetadata(unit);
    builder.buildNestedClass(metadata, className, superClass, nestBuilder -> {
      var def = AnyDef.fromVar(unit.ref());
      buildInstance(nestBuilder, NameSerializer.getClassDesc(def));
      if (shouldBuildEmptyCall(unit)) {
        nestBuilder.buildConstantField(FreeUtil.fromClass(callClass()), FIELD_EMPTYCALL, cb ->
          buildEmptyCall(cb, def));
      }

      buildConstructor(nestBuilder, unit);
      continuation.accept(nestBuilder);
    });
  }

  public abstract @NotNull JitDefSerializer<T> serialize(@NotNull FreeClassBuilder builder, T unit);
}

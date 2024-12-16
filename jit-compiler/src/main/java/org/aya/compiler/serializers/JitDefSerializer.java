// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.MethodRef;
import org.aya.compiler.serializers.ModuleSerializer.MatchyRecorder;
import org.aya.syntax.compile.CompiledAya;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.function.Consumer;

import static org.aya.compiler.serializers.AyaSerializer.FIELD_EMPTYCALL;
import static org.aya.compiler.serializers.AyaSerializer.STATIC_FIELD_INSTANCE;
import static org.aya.compiler.serializers.NameSerializer.javifyClassName;

public abstract class JitDefSerializer<T extends TyckDef> {
  protected final @NotNull Class<?> superClass;
  protected final @NotNull MatchyRecorder recorder;

  protected JitDefSerializer(@NotNull Class<?> superClass, @NotNull MatchyRecorder recorder) {
    this.superClass = superClass;
    this.recorder = recorder;
  }

  private static @NotNull CompiledAya mkCompiledAya(
    @NotNull String[] module, int fileModuleSize,
    @NotNull String name, int assoc, int shape,
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

  protected @NotNull FieldRef buildInstance(@NotNull FreeClassBuilder builder, @NotNull MethodRef con) {
    return builder.buildConstantField(con.owner(), STATIC_FIELD_INSTANCE, b ->
      b.mkNew(con, ImmutableSeq.empty()));
  }

  protected abstract boolean shouldBuildEmptyCall(@NotNull T unit);

  protected abstract @NotNull Class<?> callClass();

  protected abstract @NotNull MethodRef buildConstructor(@NotNull FreeClassBuilder builder, T unit);

  protected final FreeJavaExpr buildEmptyCall(@NotNull FreeExprBuilder builder, @NotNull AnyDef def) {
    return builder.mkNew(callClass(), ImmutableSeq.of(AbstractExprializer.getInstance(builder, def)));
  }

  protected void buildFramework(@NotNull FreeClassBuilder builder, @NotNull T unit, @NotNull Consumer<FreeClassBuilder> continuation) {
    var className = javifyClassName(unit.ref());
    var metadata = buildMetadata(unit);
    builder.buildNestedClass(metadata, className, superClass, nestBuilder -> {
      var def = AnyDef.fromVar(unit.ref());
      var con = buildConstructor(nestBuilder, unit);
      buildInstance(nestBuilder, con);
      if (shouldBuildEmptyCall(unit)) {
        nestBuilder.buildConstantField(FreeUtil.fromClass(callClass()), FIELD_EMPTYCALL, cb ->
          buildEmptyCall(cb, def));
      }

      continuation.accept(nestBuilder);
    });
  }

  public abstract @NotNull JitDefSerializer<T> serialize(@NotNull FreeClassBuilder builder, T unit);

  public @NotNull FreeJavaExpr serializeTermUnderTele(
    @NotNull FreeExprBuilder builder, @NotNull Term term,
    @NotNull FreeJavaExpr argsTerm, int size
  ) {
    return serializeTermUnderTele(builder, term, AbstractExprializer.fromSeq(builder, Constants.CD_Term, argsTerm, size));
  }

  public @NotNull FreeJavaExpr serializeTermUnderTele(
    @NotNull FreeExprBuilder builder,
    @NotNull Term term,
    @NotNull ImmutableSeq<FreeJavaExpr> argTerms
  ) {
    return new TermExprializer(builder, argTerms, recorder)
      .serialize(term);
  }

  public @NotNull FreeJavaExpr serializeTerm(@NotNull FreeCodeBuilder builder, @NotNull Term term) {
    return serializeTermUnderTele(builder, term, ImmutableSeq.empty());
  }
}

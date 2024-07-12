// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.compile.CompiledAya;
import org.aya.syntax.core.def.TyckDef;
import org.jetbrains.annotations.NotNull;

import static org.aya.compiler.AyaSerializer.*;
import static org.aya.compiler.NameSerializer.javifyClassName;

public abstract class JitDefSerializer<T extends TyckDef> extends AbstractSerializer<T> {
  public static final String CLASS_METADATA = ExprializeUtils.getJavaReference(CompiledAya.class);

  protected final @NotNull Class<?> superClass;

  protected JitDefSerializer(@NotNull SourceBuilder builder, @NotNull Class<?> superClass) {
    super(builder);
    this.superClass = superClass;
  }

  /**
   * @see CompiledAya
   */
  protected void buildMetadata(@NotNull T unit) {
    var ref = unit.ref();
    var module = ref.module;
    var assoc = ref.assoc();
    var assocIdx = assoc == null ? -1 : assoc.ordinal();
    assert module != null;
    appendLine(STR."@\{CLASS_METADATA}(");
    var modPath = module.module().module();
    appendMetadataRecord("module", ExprializeUtils.makeHalfArrayFrom(modPath.view().map(ExprializeUtils::makeString)), true);
    // Assumption: module.take(fileModule.size).equals(fileModule)
    appendMetadataRecord("fileModuleSize", Integer.toString(module.fileModuleSize()), false);
    appendMetadataRecord("name", ExprializeUtils.makeString(ref.name()), false);
    appendMetadataRecord("assoc", Integer.toString(assocIdx), false);
    buildShape(unit);

    appendLine(")");
  }

  protected void buildShape(T unit) {
    appendMetadataRecord("shape", "-1", false);
    appendMetadataRecord("recognition", ExprializeUtils.makeHalfArrayFrom(ImmutableSeq.empty()), false);
  }

  protected void appendMetadataRecord(@NotNull String name, @NotNull String value, boolean isFirst) {
    var prepend = isFirst ? "" : ", ";
    appendLine(STR."\{prepend}\{name} = \{value}");
  }

  public void buildInstance(@NotNull String className) {
    buildConstantField(className, STATIC_FIELD_INSTANCE, ExprializeUtils.makeNew(className));
  }

  public void buildSuperCall(@NotNull ImmutableSeq<String> args) {
    appendLine(STR."super(\{args.joinToString(", ")});");
  }

  protected void buildFramework(@NotNull T unit, @NotNull Runnable continuation) {
    var className = javifyClassName(unit.ref());
    buildMetadata(unit);
    buildInnerClass(className, superClass, () -> {
      buildInstance(className);
      appendLine();
      // empty return type for constructor
      buildMethod(className, ImmutableSeq.empty(), "/*constructor*/", false, () -> buildConstructor(unit));
      appendLine();
      if (unit.telescope().isEmpty()) {
        buildConstantField(callClass(), FIELD_EMPTYCALL, ExprializeUtils.makeNew(
          callClass(), ExprializeUtils.getInstance(className)));
      }
      appendLine();
      continuation.run();
    });
  }

  protected abstract @NotNull String callClass();

  /**
   * @see org.aya.syntax.compile.JitDef
   */
  protected abstract void buildConstructor(T unit);
}

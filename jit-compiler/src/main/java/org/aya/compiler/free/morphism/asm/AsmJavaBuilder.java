// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.asm;

import java.lang.constant.ClassDesc;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.aya.compiler.AsmOutputCollector;
import org.aya.compiler.free.FreeClassBuilder;
import org.aya.compiler.free.FreeJavaBuilder;
import org.aya.compiler.free.FreeUtil;
import org.aya.syntax.compile.CompiledAya;
import org.aya.syntax.core.repr.CodeShape;
import org.glavo.classfile.*;
import org.glavo.classfile.attribute.NestHostAttribute;
import org.glavo.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Resources:
/// * <a href="https://viewer.glavo.org/">ClassViewer</a>
/// * <a href="https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html">Class File Specification</a>
public record AsmJavaBuilder<C extends AsmOutputCollector>(@NotNull C collector) implements FreeJavaBuilder<C> {
  /// @param nestedName null if top level class
  /// @return the class descriptor
  public static @NotNull ClassDesc buildClass(
    @NotNull AsmOutputCollector collector,
    @Nullable CompiledAya metadata,
    @NotNull ClassDesc fileClassName,
    @Nullable String nestedName,
    @NotNull ClassDesc superclass,
    @NotNull Consumer<FreeClassBuilder> builder
  ) {
    var realClassName = nestedName == null ? fileClassName : fileClassName.nested(nestedName);
    var bc = ClassFile.of().build(realClassName, cb -> {
      cb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.SUPER);
      cb.withSuperclass(superclass);

      // region metadata

      if (metadata != null) {
        var moduleValue = AnnotationValue.ofArray(
          Arrays.stream(metadata.module()).map(AnnotationValue::ofString)
            .collect(Collectors.toList()));
        var fileModuleSizeValue = AnnotationValue.ofInt(metadata.fileModuleSize());
        var nameValue = AnnotationValue.ofString(metadata.name());
        var assocValue = AnnotationValue.ofInt(metadata.assoc());
        var shapeValue = AnnotationValue.ofInt(metadata.shape());
        var recognitionValue = AnnotationValue.ofArray(
          Arrays.stream(metadata.recognition()).map(x -> AnnotationValue.ofEnum(FreeUtil.fromClass(CodeShape.GlobalId.class), x.name()))
            .collect(Collectors.toList())
        );

        cb.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(
          FreeUtil.fromClass(CompiledAya.class),
          AnnotationElement.of(CompiledAya.NAME_MODULE, moduleValue),
          AnnotationElement.of(CompiledAya.NAME_FILE_MODULE_SIZE, fileModuleSizeValue),
          AnnotationElement.of(CompiledAya.NAME_NAME, nameValue),
          AnnotationElement.of(CompiledAya.NAME_ASSOC, assocValue),
          AnnotationElement.of(CompiledAya.NAME_SHAPE, shapeValue),
          AnnotationElement.of(CompiledAya.NAME_RECOGNITION, recognitionValue)
        )));
      }

      // endregion metadata

      var acb = new AsmClassBuilder(realClassName, superclass, cb, collector);
      builder.accept(acb);
      acb.postBuild();

      if (nestedName != null) {
        cb.with(NestHostAttribute.of(fileClassName));
      }
    });

    collector.write(realClassName, bc);
    return realClassName;
  }

  @Override public @NotNull C buildClass(
    @Nullable CompiledAya metadata,
    @NotNull ClassDesc className,
    @NotNull Class<?> superclass,
    @NotNull Consumer<FreeClassBuilder> builder
  ) {
    buildClass(collector, metadata, className, null, FreeUtil.fromClass(superclass), builder);
    return collector;
  }
}

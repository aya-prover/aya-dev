// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.asm;

import kala.collection.mutable.MutableList;
import org.aya.compiler.AsmOutputCollector;
import org.aya.compiler.morphism.JavaUtil;
import org.aya.syntax.compile.AyaMetadata;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.util.ArrayUtil;
import org.glavo.classfile.*;
import org.glavo.classfile.attribute.NestHostAttribute;
import org.glavo.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// Resources:
/// * <a href="https://viewer.glavo.org/">ClassViewer</a>
/// * <a href="https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html">Class File Specification</a>
public record AsmJavaBuilder<C extends AsmOutputCollector>(@NotNull C collector) {
  public static void buildClass(
    @NotNull AsmOutputCollector collector,
    @Nullable AyaMetadata metadata,
    @NotNull ClassData classData,
    @NotNull ClassHierarchyResolver hierarchyResolver,
    @NotNull Consumer<AsmClassBuilder> builder
  ) {
    var realClassName = classData.className();
    var bc = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(hierarchyResolver)).build(realClassName, cb -> {
      cb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.SUPER);
      cb.withSuperclass(classData.classSuper());

      // region metadata

      if (metadata != null) {
        var moduleValue = AnnotationValue.ofArray(
          Arrays.stream(metadata.module()).map(AnnotationValue::ofString)
            .collect(Collectors.toList()));
        var fileModuleSizeValue = AnnotationValue.ofInt(metadata.fileModuleSize());
        var nameValue = AnnotationValue.ofString(metadata.name());

        var attributes = MutableList.of(
          AnnotationElement.of(AyaMetadata.NAME_MODULE, moduleValue),
          AnnotationElement.of(AyaMetadata.NAME_FILE_MODULE_SIZE, fileModuleSizeValue),
          AnnotationElement.of(AyaMetadata.NAME_NAME, nameValue)
        );
        if (metadata.assoc() != -1) {
          var assocValue = AnnotationValue.ofInt(metadata.assoc());
          attributes.append(AnnotationElement.of(AyaMetadata.NAME_ASSOC, assocValue));
        }
        if (metadata.shape() != -1) {
          var shapeValue = AnnotationValue.ofInt(metadata.shape());
          attributes.append(AnnotationElement.of(AyaMetadata.NAME_SHAPE, shapeValue));
        }
        if (metadata.recognition().length != 0) {
          var recognitionValue = AnnotationValue.ofArray(ArrayUtil.map(metadata.recognition(),
            new AnnotationValue[0], x ->
              AnnotationValue.ofEnum(JavaUtil.fromClass(CodeShape.GlobalId.class), x.name()))
          );
          attributes.append(AnnotationElement.of(AyaMetadata.NAME_RECOGNITION, recognitionValue));
        }
        if (metadata.instanceOfClass() != Object.class) {
          var instanceOfClassValue = AnnotationValue.ofClass(
            JavaUtil.fromClass(metadata.instanceOfClass()));
          attributes.append(AnnotationElement.of(AyaMetadata.INSTANCE_OF_CLASS, instanceOfClassValue));
        }

        cb.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(
          JavaUtil.fromClass(AyaMetadata.class),
          attributes.asJava()
        )));
      }

      // endregion metadata

      try (var acb = new AsmClassBuilder(classData, cb, collector, hierarchyResolver)) {
        builder.accept(acb);
      }

      if (classData.outer() != null) {
        cb.with(NestHostAttribute.of(classData.outer().data().className()));
      }
    });

    collector.write(realClassName, bc);
  }

  public @NotNull C buildClass(
    @Nullable AyaMetadata metadata,
    @NotNull ClassDesc className,
    @NotNull Class<?> superclass,
    @NotNull ClassHierarchyResolver hierarchyResolver,
    @NotNull Consumer<AsmClassBuilder> builder
  ) {
    var classData = new ClassData(className, JavaUtil.fromClass(superclass), null);
    buildClass(collector, metadata, classData, hierarchyResolver, builder);
    return collector;
  }
}

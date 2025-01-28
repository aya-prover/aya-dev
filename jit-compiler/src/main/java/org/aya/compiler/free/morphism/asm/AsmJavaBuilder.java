// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.asm;

import kala.collection.mutable.MutableList;
import org.aya.compiler.free.FreeClassBuilder;
import org.aya.compiler.free.FreeJavaBuilder;
import org.aya.compiler.free.FreeUtil;
import org.aya.syntax.compile.CompiledAya;
import org.glavo.classfile.AccessFlag;
import org.glavo.classfile.AccessFlags;
import org.glavo.classfile.ClassFile;
import org.glavo.classfile.attribute.InnerClassesAttribute;
import org.glavo.classfile.attribute.NestHostAttribute;
import org.glavo.classfile.attribute.NestMembersAttribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

public record AsmJavaBuilder(@NotNull AsmOutputCollector collector) implements FreeJavaBuilder<AsmOutputCollector> {
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
      // TODO: metadata
      cb.withSuperclass(superclass);
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

  @Override
  public @NotNull AsmOutputCollector buildClass(
    @Nullable CompiledAya metadata,
    @NotNull ClassDesc className,
    @NotNull Class<?> superclass,
    @NotNull Consumer<FreeClassBuilder> builder
  ) {
    buildClass(collector, metadata, className, null, FreeUtil.fromClass(superclass), builder);
    return collector;
  }
}

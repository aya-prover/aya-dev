// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism;

import org.aya.compiler.SourceBuilder;
import org.aya.compiler.free.FreeClassBuilder;
import org.aya.compiler.free.FreeJavaBuilder;
import org.aya.compiler.free.FreeUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

public record SourceFreeJavaBuilder(@NotNull SourceBuilder sourceBuilder)
  implements FreeJavaBuilder<String> {
  public static @NotNull SourceFreeJavaBuilder create() {
    return new SourceFreeJavaBuilder(new SourceBuilder());
  }

  // convert "Ljava/lang/Object;" to "java.lang.Object"
  public static @NotNull String toClassRef(@NotNull ClassDesc className) {
    var arrayDepth = 0;
    ClassDesc baseType = className;
    while (baseType.isArray()) {
      baseType = baseType.componentType();
      arrayDepth += 1;
    }

    var arrayPostfix = "[]".repeat(arrayDepth);
    var name = baseType.displayName();
    var packageName = baseType.packageName();
    var prefix = packageName.isEmpty() ? "" : packageName + ".";
    return prefix + name.replace('$', '.') + arrayPostfix;
  }

  // convert "Ljava/lang/Object;" to "Object"
  public static @NotNull String toClassName(@NotNull ClassDesc className) {
    var name = className.displayName();
    return name.substring(name.lastIndexOf('$') + 1);
  }

  @Override
  public @NotNull String buildClass(
    @NotNull ClassDesc className,
    @NotNull Class<?> superclass,
    @NotNull Consumer<FreeClassBuilder> builder
  ) {
    sourceBuilder.appendLine("package " + className.packageName() + ";");
    sourceBuilder.buildClass(className.displayName(), toClassRef(FreeUtil.fromClass(superclass)), false, () ->
      builder.accept(new SourceClassBuilder(this, className, sourceBuilder)));
    return sourceBuilder.builder.toString();
  }
}

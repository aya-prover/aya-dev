// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package source;

import kala.collection.Seq;
import org.aya.compiler.morphism.AstUtil;
import org.aya.compiler.morphism.ClassBuilder;
import org.aya.compiler.morphism.JavaBuilder;
import org.aya.compiler.serializers.ExprializeUtil;
import org.aya.syntax.compile.AyaMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

public record SourceFreeJavaBuilder(@NotNull SourceBuilder sourceBuilder)
  implements JavaBuilder<String> {
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

  public static final @NotNull Seq<String> warningsToSuppress = Seq.of(
    "unchecked", "rawtypes", "NullableProblems", "SwitchStatementWithTooFewBranches",
    "CodeBlock2Expr", "unused", "ConstantValue", "RedundantCast", "UnusedAssignment",
    "DataFlowIssue", "LoopStatementThatDoesntLoop", "UnnecessaryLocalVariable"
  );
  @Override public @NotNull String buildClass(
    @Nullable AyaMetadata metadata,
    @NotNull ClassDesc className,
    @NotNull Class<?> superclass,
    @NotNull Consumer<ClassBuilder> builder
  ) {
    sourceBuilder.appendLine("package " + className.packageName() + ";");
    var cb = new SourceClassBuilder(this, className, sourceBuilder);
    if (metadata != null) cb.buildMetadata(metadata);
    cb.sourceBuilder().appendLine(warningsToSuppress.joinToString(", ",
      "@SuppressWarnings(value = {", "})", ExprializeUtil::makeString));
    sourceBuilder.buildClass(className.displayName(),
      toClassRef(AstUtil.fromClass(superclass)), false, () ->
        builder.accept(cb));
    return sourceBuilder.builder.toString();
  }
}

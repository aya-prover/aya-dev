// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static org.aya.compiler.AyaSerializer.CLASS_PANIC;
import static org.aya.compiler.ExprializeUtils.getJavaReference;
import static org.aya.compiler.ExprializeUtils.makeString;

public interface SourceBuilder {
  final class Default implements SourceBuilder {
    private final @NotNull StringBuilder builder;
    private int indent;
    private final @NotNull NameGenerator nameGenerator;

    public Default() {
      this(new StringBuilder(), 0, new NameGenerator());
    }

    public Default(@NotNull StringBuilder builder, int indent, @NotNull NameGenerator nameGenerator) {
      this.builder = builder;
      this.indent = indent;
      this.nameGenerator = nameGenerator;
    }
    @Override public @NotNull StringBuilder builder() { return builder; }
    @Override public int indent() { return indent; }
    @Override public @NotNull NameGenerator nameGen() { return nameGenerator; }
    @Override public void runInside(@NotNull Runnable runnable) {
      indent++;
      runnable.run();
      indent--;
    }
  }

  @NotNull StringBuilder builder();
  int indent();
  @NotNull NameGenerator nameGen();

  default void fillIndent() {
    if (indent() == 0) return;
    builder().append("  ".repeat(indent()));
  }

  void runInside(@NotNull Runnable runnable);

  default @NotNull String buildLocalVar(@NotNull String type, @NotNull String name, @Nullable String initial) {
    var update = initial == null ? "" : STR." = \{initial}";
    appendLine(STR."\{type} \{name}\{update};");
    return name;
  }

  default void buildUpdate(@NotNull String lhs, @NotNull String rhs) {
    appendLine(STR."\{lhs} = \{rhs};");
  }

  default void buildIf(@NotNull String condition, @NotNull Runnable onSucc) {
    buildIfElse(condition, onSucc, null);
  }

  default void buildIfElse(@NotNull String condition, @NotNull Runnable onSucc, @Nullable Runnable onFailed) {
    appendLine(STR."if (\{condition}) {");
    runInside(onSucc);
    if (onFailed == null) appendLine("}");
    else {
      appendLine("} else {");
      runInside(onFailed);
      appendLine("}");
    }
  }

  /**
   * Generate java code that check whether {@param term} is an instance of {@param type}
   *
   * @param onSucc the argument is a local variable that has type {@param type} and identical equal to {@param term};
   */
  default void buildIfInstanceElse(
    @NotNull String term,
    @NotNull String type,
    @NotNull Consumer<String> onSucc,
    @Nullable Runnable onFailed
  ) {
    String name = nameGen().nextName();
    buildIfElse(STR."\{term} instanceof \{type} \{name}",
      () -> onSucc.accept(name),
      onFailed);
  }

  default void buildGoto(@NotNull Runnable continuation) {
    appendLine("do {");
    runInside(continuation);
    appendLine("} while (false);");
  }
  default void buildBreak() { appendLine("break;"); }
  default void buildReturn(@NotNull String retWith) { appendLine(STR."return \{retWith};"); }
  default void buildComment(@NotNull String comment) { appendLine("// " + comment); }
  default void buildPanic(@Nullable String message) {
    message = message == null ? "" : makeString(message);
    appendLine(STR."throw new \{CLASS_PANIC}(\{message});");
  }

  default void buildInnerClass(@NotNull String className, @Nullable Class<?> superClass, @NotNull Runnable continuation) {
    buildClass(className, superClass, true, continuation);
  }

  default void buildClass(
    @NotNull String className,
    @Nullable Class<?> superClass,
    boolean isStatic,
    @NotNull Runnable continuation
  ) {
    var ext = superClass == null ? "" : STR."extends \{getJavaReference(superClass)}";

    appendLine(STR."public \{isStatic ? "static" : ""} final class \{className} \{ext} {");
    runInside(continuation);
    appendLine("}");
  }

  static @NotNull ImmutableSeq<String> fromSeq(@NotNull String term, int size) {
    return ImmutableSeq.fill(size, idx -> STR."\{term}.get(\{idx})");
  }

  default void appendLine(@NotNull String string) {
    fillIndent();
    builder().append(string);
    builder().append('\n');
  }
  default void appendLine() { builder().append('\n'); }
  default void buildConstantField(
    @NotNull String type,
    @NotNull String name,
    @NotNull String value
  ) {
    appendLine(STR."public static final \{type} \{name} = \{value};");
  }

  default <R> void buildSwitch(
    @NotNull String term,
    @NotNull ImmutableSeq<R> cases,
    @NotNull Consumer<R> continuation,
    @NotNull Runnable defaultCase
  ) {
    if (cases.isEmpty()) {
      defaultCase.run();
      return;
    }
    appendLine(STR."switch (\{term}) {");
    runInside(() -> {
      for (var kase : cases) {
        appendLine(STR."case \{kase} -> {");
        runInside(() -> continuation.accept(kase));
        appendLine("}");
      }

      appendLine("default -> {");
      runInside(defaultCase);
      appendLine("}");
    });
    appendLine("}");
  }

  default void buildMethod(
    @NotNull String name,
    @NotNull ImmutableSeq<AbstractSerializer.JitParam> params,
    @NotNull String returnType,
    boolean override,
    @NotNull Runnable continuation
  ) {
    var paramStr = params.joinToString(", ",
      STR."\{override ? "@Override " : ""}public \{returnType} \{name}(", ") {",
      param -> STR."\{param.type()} \{param.name()}");
    appendLine(paramStr);
    runInside(continuation);
    appendLine("}");
  }
}

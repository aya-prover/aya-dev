// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package source;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static org.aya.compiler.serializers.ExprializeUtil.CLASS_PANIC;
import static org.aya.compiler.serializers.ExprializeUtil.makeString;

public final class SourceBuilder {
  public final @NotNull StringBuilder builder;
  public final @NotNull NameGenerator nameGen;
  private int indent;
  private boolean isLineBegin;
  
  public SourceBuilder() {
    this.builder = new StringBuilder();
    this.nameGen = new NameGenerator();
    this.indent = 0;
    this.isLineBegin = true;
  }
  
  public record JitParam(@NotNull String name, @NotNull String type) { }
  private void assertLineBegin() { assert isLineBegin; }

  public void runInside(@NotNull Runnable runnable) {
    indent++;
    runnable.run();
    indent--;
  }


  public int indent() { return this.indent; }

  private void fillIndent() {
    assertLineBegin();
    if (indent() == 0) return;
    builder.append("  ".repeat(indent()));
  }

  public @NotNull String buildLocalVar(@NotNull String type, @NotNull String name, @Nullable String initial) {
    var update = initial == null ? "" : " = " + initial;
    appendLine(type + " " + name + update + ";");
    return name;
  }

  public void buildUpdate(@NotNull String lhs, @NotNull String rhs) {
    appendLine(lhs + " = " + rhs + ";");
  }

  public void buildIf(@NotNull String condition, @NotNull Runnable onSucc) {
    buildIfElse(condition, onSucc, null);
  }

  public void buildIfElse(@NotNull String condition, @NotNull Runnable onSucc, @Nullable Runnable onFailed) {
    appendLine("if (" + condition + ") {");
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
  public void buildIfInstanceElse(
    @NotNull String term,
    @NotNull String type,
    @NotNull Consumer<String> onSucc,
    @Nullable Runnable onFailed
  ) {
    String name = nameGen.nextName();
    buildIfElse(term + " instanceof " + type + " " + name,
      () -> onSucc.accept(name),
      onFailed);
  }

  public void buildGoto(@NotNull Runnable continuation) {
    appendLine("do {");
    runInside(continuation);
    appendLine("} while (false);");
  }

  public void buildBreak() { appendLine("break;"); }
  public void buildContinue() { appendLine("continue;"); }
  public void buildReturn(@NotNull String retWith) { appendLine("return " + retWith + ";"); }
  public void buildComment(@NotNull String comment) { appendLine("// " + comment); }
  public void buildPanic(@Nullable String message) {
    message = message == null ? "" : makeString(message);
    appendLine("throw new " + CLASS_PANIC + "(" + message + ");");
  }

  public void buildClass(
    @NotNull String className, @Nullable String superClass,
    boolean isStatic, @NotNull Runnable continuation
  ) {
    var ext = superClass == null ? "" : "extends " + superClass;

    appendLine("public " + (isStatic ? "static" : "") + " final class " + className + " " + ext + " {");
    runInside(continuation);
    appendLine("}");
  }

  static @NotNull ImmutableSeq<String> fromSeq(@NotNull String term, int size) {
    return ImmutableSeq.fill(size, idx -> term + ".get(" + idx + ")");
  }

  public void appendLine(@NotNull String string) {
    fillIndent();
    builder.append(string);
    appendLine();
  }

  public void append(@NotNull String string) {
    if (isLineBegin) fillIndent();
    isLineBegin = false;
    builder.append(string);
  }

  public void appendLine() {
    builder.append('\n');
    isLineBegin = true;
  }

  public void buildConstantField(
    @NotNull String type,
    @NotNull String name,
    @Nullable String value
  ) {
    if (value != null) {
      value = " = " + value;
    } else {
      value = "";
    }

    appendLine("public static final " + type + " " + name + value + ";");
  }

  public void buildSwitch(
    @NotNull String term,
    @NotNull ImmutableIntSeq cases,
    @NotNull IntConsumer continuation,
    @NotNull Runnable publicCase
  ) {
    if (cases.isEmpty()) {
      publicCase.run();
      return;
    }
    appendLine("switch (" + term + ") {");
    runInside(() -> {
      cases.forEach(kase -> {
        appendLine("case " + kase + " -> {");
        runInside(() -> continuation.accept(kase));
        appendLine("}");
      });

      appendLine("default -> {");
      runInside(publicCase);
      appendLine("}");
    });
    appendLine("}");
  }

  public void buildMethod(
    @NotNull String name,
    @NotNull ImmutableSeq<JitParam> params,
    @NotNull String returnType,
    boolean override,
    @NotNull Runnable continuation
  ) {
    var paramStr = params.joinToString(", ",
      (override ? "@Override " : "") + "public " + returnType + " " + name + "(", ") {",
      param -> param.type() + " " + param.name());
    appendLine(paramStr);
    runInside(continuation);
    appendLine("}");
  }
}

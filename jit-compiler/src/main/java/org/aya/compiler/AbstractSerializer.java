// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.NameGenerator;
import org.aya.syntax.compile.JitDef;
import org.aya.syntax.compile.JitTele;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.ModulePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public abstract class AbstractSerializer<T> implements AyaSerializer<T> {
  public record JitParam(@NotNull String name, @NotNull String type) { }

  protected final @NotNull StringBuilder builder;
  protected int indent;
  protected final @NotNull NameGenerator nameGen;

  protected AbstractSerializer(@NotNull StringBuilder builder, int indent, @NotNull NameGenerator nameGen) {
    this.builder = builder;
    this.indent = indent;
    this.nameGen = nameGen;
  }

  protected AbstractSerializer(@NotNull AbstractSerializer<?> other) {
    this(other.builder, other.indent, other.nameGen);
  }

  /**
   * the implementation should keep {@link #indent} after invocation.
   */
  @Override
  public abstract AyaSerializer<T> serialize(T unit);

  @Override
  public String result() {
    return builder.toString();
  }

  public void fillIndent() {
    if (indent == 0) return;
    builder.append("  ".repeat(indent));
  }

  public void runInside(@NotNull Runnable runnable) {
    indent++;
    runnable.run();
    indent--;
  }

  public @NotNull String buildLocalVar(@NotNull String type, @NotNull String name, @Nullable String initial) {
    var update = initial == null ? "" : STR." = \{initial}";
    appendLine(STR."\{type} \{name}\{update};");
    return name;
  }

  public void buildUpdate(@NotNull String lhs, @NotNull String rhs) {
    appendLine(STR."\{lhs} = \{rhs};");
  }

  public void buildIf(@NotNull String condition, @NotNull Runnable onSucc) {
    buildIfElse(condition, onSucc, null);
  }

  public void buildIfElse(@NotNull String condition, @NotNull Runnable onSucc, @Nullable Runnable onFailed) {
    appendLine(STR."if (\{condition}) {");
    runInside(onSucc);
    if (onFailed == null) {
      appendLine("}");
    } else {
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
    String name = nameGen.nextName(null);
    buildIfElse(STR."\{term} instanceof \{type} \{name}",
      () -> onSucc.accept(name),
      onFailed);
  }

  public void buildGoto(@NotNull Runnable continuation) {
    appendLine("do {");
    runInside(continuation);
    appendLine("} while (false);");
  }

  public void buildBreak() {
    appendLine("break;");
  }

  public void buildReturn(@NotNull String retWith) {
    appendLine(STR."return \{retWith};");
  }

  public void buildPanic(@Nullable String message) {
    message = message == null ? "" : makeString(message);
    appendLine(STR."throw new \{CLASS_PANIC}(\{message});");
  }

  public void buildInnerClass(@NotNull String className, @Nullable Class<?> superClass, @NotNull Runnable continuation) {
    buildClass(className, superClass, true, continuation);
  }

  public void buildClass(
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

  public @NotNull ImmutableSeq<String> buildGenLocalVarsFromSeq(@NotNull String type, @NotNull String seqTerm, int size) {
    String[] names = new String[size];
    for (int i = 0; i < size; ++i) {
      var name = nameGen.nextName(null);
      names[i] = name;
      buildLocalVar(type, name, STR."\{seqTerm}.get(\{i})");
    }

    return ImmutableArray.Unsafe.wrap(names);
  }

  public static @NotNull ImmutableSeq<String> fromSeq(@NotNull String term, int size) {
    return ImmutableSeq.fill(size, idx -> STR."\{term}.get(\{idx})");
  }

  public void appendLine(@NotNull String string) {
    fillIndent();
    builder.append(string);
    builder.append('\n');
  }

  public void appendLine() {
    builder.append('\n');
  }

  public <R> void buildSwitch(
    @NotNull String term,
    @NotNull ImmutableSeq<R> cases,
    @NotNull Consumer<R> continuation
  ) {
    buildSwitch(term, cases, continuation, () -> buildPanic(null));
  }

  public <R> void buildSwitch(
    @NotNull String term,
    @NotNull ImmutableSeq<R> cases,
    @NotNull Consumer<R> continuation,
    @NotNull Runnable defaultCase
  ) {
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

  public void buildMethod(
    @NotNull String name,
    @NotNull ImmutableSeq<JitParam> params,
    @NotNull String returnType,
    boolean override,
    @NotNull Runnable continuation
  ) {
    if (override) {
      appendLine("@Override");
    }

    var paramStr = params.joinToString(", ", param -> STR."\{param.type()} \{param.name()}");
    appendLine(STR."public \{returnType} \{name}(\{paramStr}) {");
    runInside(continuation);
    appendLine("}");
  }

  protected static @NotNull String makeArrayFrom(@NotNull String type, @NotNull ImmutableSeq<String> elements) {
    return STR."new \{type}[] \{makeHalfArrayFrom(elements)}";
  }

  protected static @NotNull String makeHalfArrayFrom(@NotNull SeqLike<String> elements) {
    return elements.joinToString(", ", "{ ", " }");
  }

  protected static @NotNull String makeSub(@NotNull String superClass, @NotNull String sub) {
    return STR."\{superClass}.\{sub}";
  }

  protected @NotNull String serializeTermUnderTele(@NotNull Term term, @NotNull String argsTerm, int size) {
    return serializeTermUnderTele(term, fromSeq(argsTerm, size));
  }

  protected @NotNull String serializeTermUnderTele(@NotNull Term term, @NotNull ImmutableSeq<String> argTerms) {
    return new TermExprializer(this.nameGen, argTerms)
      .serialize(term).result();
  }

  protected static @NotNull String makeString(@NotNull String raw) {
    // TODO: kala bug
    // assert StringView.of(raw).anyMatch(c -> c == '\\' || c == '"');
    return STR."\"\{StringUtil.escapeStringCharacters(raw)}\"";
  }

  protected static @NotNull String isNull(@NotNull String term) {
    return STR."\{term} == null";
  }

  public static @NotNull String getModuleReference(@Nullable ModulePath module) {
    return (module == null ? SeqView.<String>empty() : module.module().view())
      .prepended(PACKAGE_BASE).joinToString(".");
  }

  protected static @NotNull String getCoreReference(@NotNull DefVar<?, ?> ref) {
    return STR."\{getModuleReference(Objects.requireNonNull(ref.module).module())}.\{javify(ref)}";
  }

  // TODO: produce name like "AYA_Data_Vec_Vec" rather than just "Vec", so that they won't conflict with our import
  // then we can make all `CLASS_*` thing become unqualified.
  protected static @NotNull String getJitReference(@NotNull JitTele ref) {
    return ref.getClass().getName();
  }

  protected static @NotNull String getReference(@NotNull AnyDef def) {
    return switch (def) {
      case JitDef jitDef -> getJitReference(jitDef);
      case TyckAnyDef<?> tyckDef -> getCoreReference(tyckDef.ref);
    };
  }

  protected static @NotNull String getInstance(@NotNull String defName) {
    return STR."\{defName}.\{STATIC_FIELD_INSTANCE}";
  }

  protected static @NotNull String getCallInstance(@NotNull String term) {
    return STR."\{term}.\{FIELD_INSTANCE}()";
  }

  /** Mangle an aya symbol name to a java symbol name */
  public static @NotNull String javify(@NotNull DefVar<?, ?> ayaName) {
    return javify(ayaName.name());
  }
  public static @NotNull String javify(String name) {
    return name.codePoints().flatMap(x ->
        x == '$' ? "$$".chars()
          : Character.isJavaIdentifierPart(x) ? IntStream.of(x)
            : ("$" + x).chars())
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
      .toString();
  }

  public static @NotNull String getJavaReference(@NotNull Class<?> clazz) {
    return clazz.getSimpleName().replace('$', '.');
  }
}

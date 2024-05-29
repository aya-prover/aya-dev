// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.NameGenerator;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.ref.QName;
import org.aya.syntax.ref.QPath;
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

  /**
   * Compute the package reference of certain <b>file level</b> {@link ModulePath}.
   */
  public static @NotNull String getModulePackageReference(@NotNull ModulePath module, @NotNull String separator) {
    return module.module().view().dropLast(1)
      .prepended(PACKAGE_BASE)
      .joinToString(separator, AbstractSerializer::javify);
  }

  public static @NotNull String getModuleReference(@NotNull QPath module) {
    return getReference(module, null);
  }

  public static @NotNull String getReference(@NotNull QName name) {
    return getReference(name.module(), name.name());
  }

  public static @NotNull String getClassName(@NotNull QPath module, @Nullable String name) {
    return getReference(module, name, "$");
  }

  public static @NotNull String getReference(@NotNull QPath module, @Nullable String name) {
    return getReference(module, name, ".");
  }

  /**
   * Compute the qualified name for certain {@link QPath module}/symbol in {@link QPath module}.
   * You may want to specify {@param separator} for different use.
   */
  public static @NotNull String getReference(@NotNull QPath module, @Nullable String name, @NotNull String separator) {
    // get package name of file level module
    var packageName = getModulePackageReference(module.fileModule(), ".");
    // get javify class name of each component
    var javifyComponent = module.traversal((path) -> javifyClassName(path, null)).view();
    if (name != null) javifyComponent = javifyComponent.appended(javifyClassName(module, name));
    return STR."\{packageName}.\{javifyComponent.joinToString(separator)}";
  }

  protected static @NotNull String getCoreReference(@NotNull DefVar<?, ?> ref) {
    return getReference(TyckAnyDef.make(ref.core));
  }

  /**
   * Obtain the java qualified name of certain {@link AnyDef def}
   *
   * @see #getReference(QPath, String, String)
   */
  protected static @NotNull String getReference(@NotNull AnyDef def) {
    return getReference(def.qualifiedName());
  }

  protected static @NotNull String getInstance(@NotNull String defName) {
    return STR."\{defName}.\{STATIC_FIELD_INSTANCE}";
  }

  protected static @NotNull String getCallInstance(@NotNull String term) {
    return STR."\{term}.\{FIELD_INSTANCE}()";
  }

  /** Mangle an aya symbol name to a java symbol name */
  public static @NotNull String javifyClassName(@NotNull DefVar<?, ?> ayaName) {
    return javifyClassName(Objects.requireNonNull(ayaName.module), ayaName.name());
  }

  public static @NotNull String javifyClassName(@NotNull QPath path, @Nullable String name) {
    var ids = path.module().module()
      .view().drop(path.fileModuleSize() - 1);
    if (name != null) ids = ids.appended(name);
    return javifyClassName(ids);
  }

  /**
   * Generate a java friendly class name of {@param ids}, this function should be one-to-one
   *
   * @param ids the ids that has form {@code [ FILE_MODULE , VIRTUAL_MODULE* , NAME? ]}
   */
  public static @NotNull String javifyClassName(@NotNull SeqView<String> ids) {
    return ids.map(AbstractSerializer::javify)
      .joinToString("$", "$", "");
  }

  /**
   * Generate a java friendly name for {@param name}, this function should be one-to-one.
   * Note that the result may not be used for class name, see {@link #javifyClassName}
   */
  public static @NotNull String javify(String name) {
    return name.codePoints().flatMap(x ->
        x == '$' ? "$$".chars()
          : Character.isJavaIdentifierPart(x) ? IntStream.of(x)
            : ("$" + x).chars())
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
      .toString();
  }

  /**
   * Get the reference to {@param clazz}, it should be imported to current file.
   */
  public static @NotNull String getJavaReference(@NotNull Class<?> clazz) {
    return clazz.getSimpleName().replace('$', '.');
  }
}

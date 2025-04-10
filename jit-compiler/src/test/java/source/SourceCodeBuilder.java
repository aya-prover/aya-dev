// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package source;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.compiler.FieldRef;
import org.aya.compiler.LocalVariable;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.ArgumentProvider;
import org.aya.compiler.morphism.AstUtil;
import org.aya.compiler.morphism.CodeBuilder;
import org.aya.compiler.morphism.JavaExpr;
import org.aya.compiler.serializers.ExprializeUtil;
import org.aya.util.IterableUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

import static source.SourceFreeJavaBuilder.toClassRef;

/// Basically a debugging impl for [CodeBuilder]. It produces slightly illegal Java code.
public record SourceCodeBuilder(
  @NotNull SourceClassBuilder parent,
  @NotNull SourceBuilder sourceBuilder
) implements CodeBuilder {
  public void appendArgs(@NotNull ImmutableSeq<JavaExpr> args) {
    IterableUtil.forEach(args, () -> sourceBuilder.append(", "), this::appendExpr);
  }

  public void appendExpr(@NotNull JavaExpr expr) {
    switch ((SourceFreeJavaExpr) expr) {
      case SourceFreeJavaExpr.BlackBox blackBox -> sourceBuilder.append(blackBox.expr());
      case SourceFreeJavaExpr.Cont cont -> cont.run();
    }
  }

  public static @NotNull String getExpr(@NotNull LocalVariable expr) {
    return ((SourceFreeJavaExpr.BlackBox) expr).expr();
  }

  @Override
  public void invokeSuperCon(@NotNull ImmutableSeq<ClassDesc> superConParams, @NotNull ImmutableSeq<JavaExpr> superConArgs) {
    sourceBuilder.append("super(");
    appendArgs(superConArgs);
    sourceBuilder.append(");");
    sourceBuilder.appendLine();
  }

  @Override
  public @NotNull SourceFreeJavaExpr.BlackBox makeVar(@NotNull ClassDesc type, @Nullable JavaExpr initializer) {
    var name = sourceBuilder.nameGen.nextName();

    sourceBuilder.append(toClassRef(type) + " " + name);

    if (initializer != null) {
      sourceBuilder.append(" = ");
      appendExpr(initializer);
    }

    sourceBuilder.append(";");
    sourceBuilder.appendLine();

    return new SourceFreeJavaExpr.BlackBox(name);
  }

  @Override public void updateVar(@NotNull LocalVariable var, @NotNull JavaExpr update) {
    sourceBuilder.append(getExpr(var) + " = ");
    appendExpr(update);
    sourceBuilder.append(";");
    sourceBuilder.appendLine();
  }

  // (() -> something).get()[0] = 114;
  @Override public void updateArray(@NotNull JavaExpr array, int idx, @NotNull JavaExpr update) {
    appendExpr(array);
    sourceBuilder.append("[" + idx + "]");
    sourceBuilder.append(" = ");
    appendExpr(update);
    sourceBuilder.append(";");
    sourceBuilder.appendLine();
  }

  private void buildUpdate(@NotNull String lhs, @NotNull JavaExpr rhs) {
    sourceBuilder.append(lhs);
    sourceBuilder.append(" = ");
    appendExpr(rhs);
    sourceBuilder.append(";");
    sourceBuilder.appendLine();
  }

  private void buildIf(
    @NotNull Runnable condition,
    @NotNull Consumer<CodeBuilder> thenBlock,
    @Nullable Consumer<CodeBuilder> elseBlock
  ) {
    sourceBuilder.append("if (");
    condition.run();
    sourceBuilder.append(") {");
    sourceBuilder.appendLine();
    sourceBuilder.runInside(() -> thenBlock.accept(this));
    sourceBuilder.append("}");

    if (elseBlock != null) {
      sourceBuilder.append(" else {");
      sourceBuilder.appendLine();
      sourceBuilder.runInside(() -> elseBlock.accept(this));
      sourceBuilder.appendLine("}");
    } else {
      sourceBuilder.appendLine();
    }
  }

  private void buildIf(
    @NotNull String condition,
    @NotNull Consumer<CodeBuilder> thenBlock,
    @Nullable Consumer<CodeBuilder> elseBlock
  ) {
    buildIf(() -> sourceBuilder.append(condition), thenBlock, elseBlock);
  }

  @Override public void ifNotTrue(
    @NotNull LocalVariable notTrue,
    @NotNull Consumer<CodeBuilder> thenBlock,
    @Nullable Consumer<CodeBuilder> elseBlock
  ) {
    buildIf("! (" + getExpr(notTrue) + ")", thenBlock, elseBlock);
  }

  @Override public void ifTrue(
    @NotNull LocalVariable theTrue,
    @NotNull Consumer<CodeBuilder> thenBlock,
    @Nullable Consumer<CodeBuilder> elseBlock
  ) {
    buildIf(getExpr(theTrue), thenBlock, elseBlock);
  }

  @Override public void ifInstanceOf(
    @NotNull JavaExpr lhs,
    @NotNull ClassDesc rhs,
    @NotNull BiConsumer<CodeBuilder, LocalVariable> thenBlock,
    @Nullable Consumer<CodeBuilder> elseBlock
  ) {
    var name = sourceBuilder.nameGen.nextName();
    buildIf(() -> {
        appendExpr(lhs);
        sourceBuilder.append(" instanceof " + toClassRef(rhs) + " " + name);
      }, cb -> thenBlock.accept(cb, new SourceFreeJavaExpr.BlackBox(name)),
      elseBlock);
  }

  @Override public void ifIntEqual(
    @NotNull JavaExpr lhs,
    int rhs,
    @NotNull Consumer<CodeBuilder> thenBlock,
    @Nullable Consumer<CodeBuilder> elseBlock
  ) {
    buildIf(() -> {
      appendExpr(lhs);
      sourceBuilder.append(" == " + rhs);
    }, thenBlock, elseBlock);
  }

  @Override public void ifRefEqual(
    @NotNull JavaExpr lhs,
    @NotNull JavaExpr rhs,
    @NotNull Consumer<CodeBuilder> thenBlock,
    @Nullable Consumer<CodeBuilder> elseBlock
  ) {
    buildIf(() -> {
      appendExpr(lhs);
      sourceBuilder.append(" == ");
      appendExpr(rhs);
    }, thenBlock, elseBlock);
  }

  @Override public void ifNull(
    @NotNull JavaExpr isNull,
    @NotNull Consumer<CodeBuilder> thenBlock,
    @Nullable Consumer<CodeBuilder> elseBlock
  ) {
    buildIf(() -> {
      appendExpr(isNull);
      sourceBuilder.append(" == null");
    }, thenBlock, elseBlock);
  }

  @Override public void breakable(@NotNull Consumer<CodeBuilder> innerBlock) {
    sourceBuilder.appendLine("do {");
    sourceBuilder.runInside(() -> innerBlock.accept(this));
    sourceBuilder.appendLine("} while (false);");
  }

  @Override public void breakOut() { sourceBuilder.buildBreak(); }

  @Override public void whileTrue(@NotNull Consumer<CodeBuilder> innerBlock) {
    sourceBuilder.appendLine("while (true) {");
    sourceBuilder.runInside(() -> innerBlock.accept(this));
    sourceBuilder.appendLine("}");
  }

  @Override public void continueLoop() {
    sourceBuilder.buildContinue();
  }

  @Override public void exec(@NotNull JavaExpr expr) {
    appendExpr(expr);
    sourceBuilder.append(";");
    sourceBuilder.appendLine();
  }

  @Override public void switchCase(
    @NotNull LocalVariable elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<CodeBuilder> branch,
    @NotNull Consumer<CodeBuilder> defaultCase
  ) {
    sourceBuilder.buildSwitch(getExpr(elim), cases,
      i -> branch.accept(this, i),
      () -> defaultCase.accept(this));
  }

  @Override public void returnWith(@NotNull JavaExpr expr) {
    sourceBuilder.append("return ");
    appendExpr(expr);
    sourceBuilder.append(";");
    sourceBuilder.appendLine();
  }

  private @NotNull SourceFreeJavaExpr.Cont mkNew(@NotNull ClassDesc className, @NotNull ImmutableSeq<JavaExpr> args) {
    return () -> {
      sourceBuilder.append("new " + toClassRef(className) + "(");
      appendArgs(args);
      sourceBuilder.append(")");
    };
  }

  @Override public @NotNull JavaExpr mkNew(@NotNull MethodRef conRef, @NotNull ImmutableSeq<JavaExpr> args) {
    return mkNew(conRef.owner(), args);
  }

  @Override public @NotNull JavaExpr mkNew(@NotNull Class<?> className, @NotNull ImmutableSeq<JavaExpr> args) {
    return mkNew(AstUtil.fromClass(className), args);
  }

  @Override
  public @NotNull SourceFreeJavaExpr.Cont invoke(@NotNull MethodRef method, @NotNull JavaExpr owner, @NotNull ImmutableSeq<JavaExpr> args) {
    return () -> {
      appendExpr(owner);
      sourceBuilder.append("." + method.name() + "(");
      appendArgs(args);
      sourceBuilder.append(")");
    };
  }

  @Override
  public @NotNull SourceFreeJavaExpr.Cont invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<JavaExpr> args) {
    return () -> {
      sourceBuilder.append(toClassRef(method.owner()) + "." + method.name() + "(");
      appendArgs(args);
      sourceBuilder.append(")");
    };
  }

  @Override public @NotNull SourceFreeJavaExpr.BlackBox refField(@NotNull FieldRef field) {
    return new SourceFreeJavaExpr.BlackBox(toClassRef(field.owner()) + "." + field.name());
  }

  @Override public @NotNull SourceFreeJavaExpr.Cont refField(@NotNull FieldRef field, @NotNull JavaExpr owner) {
    return () -> {
      appendExpr(owner);
      sourceBuilder.append("." + field.name());
    };
  }

  public static @NotNull String makeRefEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) {
    return toClassRef(enumClass) + "." + enumName;
  }

  @Override
  public @NotNull SourceFreeJavaExpr.BlackBox refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) {
    return new SourceFreeJavaExpr.BlackBox(makeRefEnum(enumClass, enumName));
  }

  @Override public @NotNull SourceFreeJavaExpr.Cont mkLambda(
    @NotNull ImmutableSeq<JavaExpr> captures,
    @NotNull MethodRef method,
    @NotNull BiConsumer<ArgumentProvider.Lambda, CodeBuilder> builder
  ) {
    var name = ImmutableSeq.fill(method.paramTypes().size(), _ -> sourceBuilder.nameGen.nextName());
    var ap = new SourceArgumentProvider.Lambda(captures, name);
    return () -> {
      sourceBuilder.append(name.joinToString(", ", "(", ") -> {"));
      sourceBuilder.appendLine();
      sourceBuilder.runInside(() -> builder.accept(ap, this));
      sourceBuilder.append("}");
    };
  }

  @Override public @NotNull SourceFreeJavaExpr.BlackBox iconst(int i) {
    return new SourceFreeJavaExpr.BlackBox(Integer.toString(i));
  }

  @Override public @NotNull SourceFreeJavaExpr.BlackBox iconst(boolean b) {
    return new SourceFreeJavaExpr.BlackBox(Boolean.toString(b));
  }

  @Override public @NotNull SourceFreeJavaExpr.BlackBox aconst(@NotNull String value) {
    return new SourceFreeJavaExpr.BlackBox(ExprializeUtil.makeString(value));
  }

  @Override public @NotNull SourceFreeJavaExpr.BlackBox aconstNull(@NotNull ClassDesc type) {
    return new SourceFreeJavaExpr.BlackBox("((" + toClassRef(type) + ") null)");
  }

  @Override public @NotNull SourceFreeJavaExpr.BlackBox thisRef() {
    return new SourceFreeJavaExpr.BlackBox("this");
  }

  public static @NotNull String mkHalfArray(@NotNull ImmutableSeq<String> elems) {
    return elems.joinToString(", ", "{ ", " }");
  }

  @Override
  public @NotNull SourceFreeJavaExpr.Cont mkArray(@NotNull ClassDesc type, int length, @Nullable ImmutableSeq<JavaExpr> initializer) {
    assert initializer == null || initializer.sizeEquals(length);
    var hasInit = initializer != null;
    var arrayIndicator = hasInit ? "[]" : "[" + length + "]";

    return () -> {
      sourceBuilder.append("new " + toClassRef(type) + arrayIndicator);
      if (initializer != null) {
        sourceBuilder.append(" { ");
        appendArgs(initializer);
        sourceBuilder.append(" }");
      }
    };
  }

  @Override public @NotNull SourceFreeJavaExpr.Cont getArray(@NotNull JavaExpr array, int index) {
    return () -> {
      appendExpr(array);
      sourceBuilder.append("[" + index + "]");
    };
  }

  @Override public @NotNull SourceFreeJavaExpr.Cont checkcast(@NotNull JavaExpr obj, @NotNull ClassDesc as) {
    return () -> {
      sourceBuilder.append("((" + toClassRef(as) + ") ");
      appendExpr(obj);
      sourceBuilder.append(")");
    };
  }
}

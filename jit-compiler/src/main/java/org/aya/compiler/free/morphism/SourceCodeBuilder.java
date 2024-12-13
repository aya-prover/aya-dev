// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.compiler.ExprializeUtils;
import org.aya.compiler.SourceBuilder;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.LocalVariable;
import org.aya.compiler.free.data.MethodRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.*;

import static org.aya.compiler.free.morphism.SourceFreeJavaBuilder.toClassRef;

public record SourceCodeBuilder(
  @NotNull SourceClassBuilder parent,
  @NotNull ClassDesc owner,
  @NotNull SourceBuilder sourceBuilder
) implements FreeCodeBuilder {
  public static @NotNull String toArgs(@NotNull ImmutableSeq<FreeJavaExpr> args) {
    return args.view().map(SourceCodeBuilder::getExpr).joinToString(", ");
  }

  public static @NotNull String getExpr(@NotNull FreeJavaExpr expr) {
    return ((SourceFreeJavaExpr) expr).expr();
  }

  public static @NotNull String getExpr(@NotNull LocalVariable expr) {
    return ((SourceFreeJavaExpr) expr).expr();
  }

  @Override public @NotNull FreeJavaResolver resolver() { return parent; }

  @Override
  public void invokeSuperCon(@NotNull ImmutableSeq<ClassDesc> superConParams, @NotNull ImmutableSeq<FreeJavaExpr> superConArgs) {
    sourceBuilder.appendLine("super("
      + superConArgs.map(SourceCodeBuilder::getExpr).joinToString(", ")
      + ");");
  }

  @Override
  public @NotNull SourceFreeJavaExpr makeVar(@NotNull ClassDesc type, @Nullable FreeJavaExpr initializer) {
    var mInitializer = initializer == null ? null : ((SourceFreeJavaExpr) initializer).expr();
    var name = sourceBuilder.nameGen().nextName();

    sourceBuilder.buildLocalVar(toClassRef(type), name, mInitializer);
    return new SourceFreeJavaExpr(name);
  }

  @Override
  public void updateVar(@NotNull LocalVariable var, @NotNull FreeJavaExpr update) {
    sourceBuilder.buildUpdate(getExpr(var), getExpr(update));
  }

  @Override
  public void updateArray(@NotNull FreeJavaExpr array, int idx, @NotNull FreeJavaExpr update) {
    sourceBuilder.buildUpdate(getExpr(array) + "[" + idx + "]", getExpr(update));
  }

  @Override
  public void updateField(@NotNull FieldRef field, @NotNull FreeJavaExpr update) {
    var fieldRef = toClassRef(field.owner()) + "." + field.name();
    sourceBuilder.buildUpdate(fieldRef, getExpr(update));
  }

  @Override
  public void updateField(@NotNull FieldRef field, @NotNull FreeJavaExpr owner, @NotNull FreeJavaExpr update) {
    sourceBuilder.buildUpdate(getExpr(owner) + "." + field.name(), getExpr(update));
  }

  @Override public void ifNotTrue(
    @NotNull FreeJavaExpr notTrue,
    @NotNull Consumer<FreeCodeBuilder> thenBlock,
    @Nullable Consumer<FreeCodeBuilder> elseBlock
  ) {
    buildIf("! (" + getExpr(notTrue) + ")", thenBlock, elseBlock);
  }

  private void buildIf(
    @NotNull String condition,
    @NotNull Consumer<FreeCodeBuilder> thenBlock,
    @Nullable Consumer<FreeCodeBuilder> elseBlock
  ) {
    sourceBuilder.buildIfElse(condition,
      () -> thenBlock.accept(this),
      elseBlock == null
        ? null
        : () -> elseBlock.accept(this));
  }

  @Override public void ifTrue(
    @NotNull FreeJavaExpr theTrue,
    @NotNull Consumer<FreeCodeBuilder> thenBlock,
    @Nullable Consumer<FreeCodeBuilder> elseBlock
  ) {
    buildIf(getExpr(theTrue), thenBlock, elseBlock);
  }

  @Override public void ifInstanceOf(
    @NotNull FreeJavaExpr lhs,
    @NotNull ClassDesc rhs,
    @NotNull BiConsumer<FreeCodeBuilder, LocalVariable> thenBlock,
    @Nullable Consumer<FreeCodeBuilder> elseBlock
  ) {
    var name = sourceBuilder.nameGen().nextName();
    buildIf(getExpr(lhs) + " instanceof " + toClassRef(rhs) + " " + name,
      cb -> thenBlock.accept(cb, new SourceFreeJavaExpr(name)),
      elseBlock);
  }

  @Override public void ifIntEqual(
    @NotNull FreeJavaExpr lhs,
    int rhs,
    @NotNull Consumer<FreeCodeBuilder> thenBlock,
    @Nullable Consumer<FreeCodeBuilder> elseBlock
  ) {
    buildIf(getExpr(lhs) + " == " + rhs, thenBlock, elseBlock);
  }

  @Override public void ifRefEqual(
    @NotNull FreeJavaExpr lhs,
    @NotNull FreeJavaExpr rhs,
    @NotNull Consumer<FreeCodeBuilder> thenBlock,
    @Nullable Consumer<FreeCodeBuilder> elseBlock
  ) {
    buildIf(getExpr(lhs) + " == " + getExpr(rhs), thenBlock, elseBlock);
  }

  @Override
  public void ifNull(
    @NotNull FreeJavaExpr isNull,
    @NotNull Consumer<FreeCodeBuilder> thenBlock,
    @Nullable Consumer<FreeCodeBuilder> elseBlock
  ) {
    buildIf(ExprializeUtils.isNull(getExpr(isNull)), thenBlock, elseBlock);
  }

  @Override public void breakable(@NotNull Consumer<FreeCodeBuilder> innerBlock) {
    sourceBuilder.appendLine("do {");
    sourceBuilder.runInside(() -> innerBlock.accept(this));
    sourceBuilder.appendLine("} while (false);");
  }

  @Override public void breakOut() { sourceBuilder.buildBreak(); }

  @Override public void exec(@NotNull FreeJavaExpr expr) {
    sourceBuilder.appendLine(getExpr(expr) + ";");
  }

  @Override
  public void switchCase(
    @NotNull FreeJavaExpr elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<FreeCodeBuilder> branch,
    @NotNull Consumer<FreeCodeBuilder> defaultCase
  ) {
    sourceBuilder.buildSwitch(getExpr(elim), cases,
      i -> branch.accept(this, i),
      () -> defaultCase.accept(this));
  }

  @Override
  public void returnWith(@NotNull FreeJavaExpr expr) {
    sourceBuilder.buildReturn(((SourceFreeJavaExpr) expr).expr());
  }

  @Override
  public @NotNull FreeJavaExpr mkNew(@NotNull ClassDesc className, @NotNull ImmutableSeq<FreeJavaExpr> args) {
    return new SourceFreeJavaExpr(ExprializeUtils.makeNew(toClassRef(className), toArgs(args)));
  }

  @Override
  public @NotNull FreeJavaExpr refVar(@NotNull LocalVariable name) {
    return name.ref();
  }

  @Override
  public @NotNull FreeJavaExpr invoke(@NotNull MethodRef method, @NotNull FreeJavaExpr owner, @NotNull ImmutableSeq<FreeJavaExpr> args) {
    return new SourceFreeJavaExpr(getExpr(owner) + "." + method.name() + "(" + toArgs(args) + ")");
  }

  @Override
  public @NotNull FreeJavaExpr invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<FreeJavaExpr> args) {
    return new SourceFreeJavaExpr(toClassRef(method.owner()) + "." + method.name() + "(" + toArgs(args) + ")");
  }

  @Override
  public @NotNull FreeJavaExpr refField(@NotNull FieldRef field) {
    return new SourceFreeJavaExpr(toClassRef(field.owner()) + "." + field.name());
  }

  @Override
  public @NotNull FreeJavaExpr refField(@NotNull FieldRef field, @NotNull FreeJavaExpr owner) {
    return new SourceFreeJavaExpr(getExpr(owner) + "." + field.name());
  }

  public static @NotNull String makeRefEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) {
    return toClassRef(enumClass) + "." + enumName;
  }

  @Override
  public @NotNull FreeJavaExpr refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) {
    return new SourceFreeJavaExpr(makeRefEnum(enumClass, enumName));
  }

  // FIXME: dont do this
  // We just hope user will not pass non-variable captures
  @Override
  public @NotNull FreeJavaExpr mkLambda(
    @NotNull ImmutableSeq<FreeJavaExpr> captures,
    @NotNull MethodRef method,
    @NotNull Function<ArgumentProvider.Lambda, FreeJavaExpr> builder
  ) {
    var name = ImmutableSeq.fill(method.paramTypes().size(), _ ->
      sourceBuilder.nameGen().nextName());
    var ap = new SourceArgumentProvider.Lambda(captures.map(SourceCodeBuilder::getExpr), name);
    return new SourceFreeJavaExpr("(" + name.joinToString(", ") + ") -> " + builder.apply(ap));
  }

  @Override
  public @NotNull FreeJavaExpr iconst(int i) {
    return new SourceFreeJavaExpr(Integer.toString(i));
  }

  @Override
  public @NotNull FreeJavaExpr iconst(boolean b) {
    return new SourceFreeJavaExpr(Boolean.toString(b));
  }

  @Override
  public @NotNull FreeJavaExpr aconst(@NotNull String value) {
    return new SourceFreeJavaExpr(ExprializeUtils.makeString(value));
  }

  @Override
  public @NotNull FreeJavaExpr aconstNull() {
    return new SourceFreeJavaExpr("null");
  }

  @Override
  public @NotNull FreeJavaExpr thisRef() {
    return new SourceFreeJavaExpr("this");
  }

  public static @NotNull String mkHalfArray(@NotNull ImmutableSeq<String> elems) {
    return elems.joinToString(", ", "{ ", " }");
  }

  @Override
  public @NotNull FreeJavaExpr mkArray(@NotNull ClassDesc type, int length, @NotNull ImmutableSeq<FreeJavaExpr> initializer) {
    assert initializer.isEmpty() || initializer.sizeEquals(length);
    var init = initializer.isEmpty() ? "" : mkHalfArray(initializer.map(SourceCodeBuilder::getExpr));
    return new SourceFreeJavaExpr("new " + toClassRef(type) + "[" + length + "]" + init);
  }

  @Override
  public @NotNull FreeJavaExpr getArray(@NotNull FreeJavaExpr array, int index) {
    return new SourceFreeJavaExpr(getExpr(array) + "[" + index + "]");
  }

  @Override
  public FreeJavaExpr checkcast(@NotNull FreeJavaExpr obj, @NotNull ClassDesc as) {
    return new SourceFreeJavaExpr("((" + toClassRef(as) + ")" + getExpr(obj) + ")");
  }
}

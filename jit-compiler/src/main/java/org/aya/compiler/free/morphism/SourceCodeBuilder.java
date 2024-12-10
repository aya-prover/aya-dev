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
  @NotNull SourceFreeJavaBuilder parent,
  @NotNull ClassDesc owner,
  @NotNull SourceBuilder sourceBuilder
) implements FreeCodeBuilder, FreeExprBuilder {
  public static @NotNull String toArgs(@NotNull ImmutableSeq<FreeJava> args) {
    return args.view().map(x -> ((SourceFreeJava) x).expr()).joinToString(", ");
  }

  public static @NotNull String getExpr(@NotNull FreeJava expr) {
    return ((SourceFreeJava) expr).expr();
  }

  public static @NotNull String getExpr(@NotNull LocalVariable expr) {
    return ((SourceFreeJava) expr).expr();
  }

  @Override
  public @NotNull FreeExprBuilder exprBuilder() {
    return this;
  }

  @Override
  public @NotNull FreeJavaResolver resolver() {
    return parent;
  }

  @Override
  public @NotNull SourceFreeJava makeVar(@NotNull ClassDesc type, @Nullable FreeJava initializer) {
    var mInitializer = initializer == null ? null : ((SourceFreeJava) initializer).expr();
    var name = sourceBuilder.nameGen().nextName();

    sourceBuilder.buildLocalVar(toClassRef(type), name, mInitializer);
    return new SourceFreeJava(name);
  }

  @Override
  public void updateVar(@NotNull LocalVariable var, @NotNull FreeJava update) {
    sourceBuilder.buildUpdate(getExpr(var), getExpr(update));
  }

  @Override
  public void updateArray(@NotNull FreeJava array, int idx, @NotNull FreeJava update) {
    sourceBuilder.buildUpdate(getExpr(array) + "[" + idx + "]", getExpr(update));
  }

  @Override
  public void ifNotTrue(
    @NotNull FreeJava notTrue,
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

  @Override
  public void ifTrue(
    @NotNull FreeJava theTrue,
    @NotNull Consumer<FreeCodeBuilder> thenBlock,
    @Nullable Consumer<FreeCodeBuilder> elseBlock
  ) {
    buildIf(getExpr(theTrue), thenBlock, elseBlock);
  }

  @Override
  public void ifInstanceOf(
    @NotNull FreeJava lhs,
    @NotNull ClassDesc rhs,
    @NotNull BiConsumer<FreeCodeBuilder, LocalVariable> thenBlock,
    @Nullable Consumer<FreeCodeBuilder> elseBlock
  ) {
    var name = sourceBuilder.nameGen().nextName();
    buildIf(getExpr(lhs) + " instanceof " + toClassRef(rhs) + name,
      cb -> thenBlock.accept(cb, new SourceFreeJava(name)),
      elseBlock);
  }

  @Override
  public void ifIntEqual(
    @NotNull FreeJava lhs,
    int rhs,
    @NotNull Consumer<FreeCodeBuilder> thenBlock,
    @Nullable Consumer<FreeCodeBuilder> elseBlock
  ) {
    buildIf(getExpr(lhs) + " == " + rhs, thenBlock, elseBlock);
  }

  @Override
  public void ifRefEqual(
    @NotNull FreeJava lhs,
    @NotNull FreeJava rhs,
    @NotNull Consumer<FreeCodeBuilder> thenBlock,
    @Nullable Consumer<FreeCodeBuilder> elseBlock
  ) {
    buildIf(getExpr(lhs) + " == " + getExpr(rhs), thenBlock, elseBlock);
  }

  @Override
  public void ifNull(
    @NotNull FreeJava isNull,
    @NotNull Consumer<FreeCodeBuilder> thenBlock,
    @Nullable Consumer<FreeCodeBuilder> elseBlock
  ) {
    buildIf(ExprializeUtils.isNull(getExpr(isNull)), thenBlock, elseBlock);
  }

  @Override
  public void breakable(@NotNull Consumer<FreeCodeBuilder> innerBlock) {
    sourceBuilder.appendLine("do {");
    sourceBuilder.runInside(() -> innerBlock.accept(this));
    sourceBuilder.appendLine("} while (false);");
  }

  @Override
  public void breakOut() {
    sourceBuilder.buildBreak();
  }

  @Override
  public void exec(@NotNull FreeJava expr) {
    sourceBuilder.appendLine(getExpr(expr) + ";");
  }

  @Override
  public void switchCase(
    @NotNull FreeJava elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<FreeCodeBuilder> branch,
    @NotNull Consumer<FreeCodeBuilder> defaultCase
  ) {
    sourceBuilder.buildSwitch(getExpr(elim), cases,
      i -> branch.accept(this, i),
      () -> defaultCase.accept(this));
  }

  @Override
  public void returnWith(@NotNull FreeJava expr) {
    sourceBuilder.buildReturn(((SourceFreeJava) expr).expr());
  }

  @Override
  public @NotNull FreeJava mkNew(@NotNull ClassDesc className, @NotNull ImmutableSeq<FreeJava> args) {
    return new SourceFreeJava(ExprializeUtils.makeNew(toClassRef(className), toArgs(args)));
  }

  @Override
  public @NotNull FreeJava refVar(@NotNull LocalVariable name) {
    return name.ref();
  }

  @Override
  public @NotNull FreeJava invoke(@NotNull MethodRef method, @NotNull FreeJava owner, @NotNull ImmutableSeq<FreeJava> args) {
    return new SourceFreeJava(getExpr(owner) + "." + method.name() + "(" + toArgs(args) + ")");
  }

  @Override
  public @NotNull FreeJava invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<FreeJava> args) {
    return new SourceFreeJava(toClassRef(method.owner()) + "." + method.name() + "(" + toArgs(args) + ")");
  }

  @Override
  public @NotNull FreeJava refField(@NotNull FieldRef field) {
    return new SourceFreeJava(toClassRef(field.owner()) + "." + field.name());
  }

  @Override
  public @NotNull FreeJava refField(@NotNull FieldRef field, @NotNull FreeJava owner) {
    return new SourceFreeJava(getExpr(owner) + "." + field.name());
  }

  @Override
  public @NotNull FreeJava refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) {
    return new SourceFreeJava(toClassRef(enumClass) + "." + enumName);
  }

  // FIXME: dont do this
  // We just hope user will not pass non-variable captures
  @Override
  public @NotNull FreeJava mkLambda(
    @NotNull ImmutableSeq<FreeJava> captures,
    @NotNull MethodRef method,
    @NotNull Function<ArgumentProvider.Lambda, FreeJava> builder
  ) {
    var name = ImmutableSeq.fill(method.paramTypes().size(), _ ->
      sourceBuilder.nameGen().nextName());
    var ap = new SourceArgumentProvider.Lambda(captures.map(SourceCodeBuilder::getExpr), name);
    return new SourceFreeJava("(" + name.joinToString(", ") + ") -> " + builder.apply(ap));
  }

  @Override
  public @NotNull FreeJava iconst(int i) {
    return new SourceFreeJava(Integer.toString(i));
  }

  @Override
  public @NotNull FreeJava iconst(boolean b) {
    return new SourceFreeJava(Boolean.toString(b));
  }

  @Override
  public @NotNull FreeJava aconst(@NotNull String value) {
    return new SourceFreeJava(ExprializeUtils.makeString(value));
  }

  @Override
  public @NotNull FreeJava aconstNull() {
    return new SourceFreeJava("null");
  }

  @Override
  public @NotNull FreeJava mkArray(@NotNull ClassDesc type, int length, @NotNull ImmutableSeq<FreeJava> initializer) {
    assert initializer.isEmpty() || initializer.sizeEquals(length);
    var init = initializer.isEmpty() ? "" : "{" + toArgs(initializer) + "}";
    return new SourceFreeJava("new " + toClassRef(type) + "[" + length + "]" + init);
  }

  @Override
  public @NotNull FreeJava getArray(@NotNull FreeJava array, int index) {
    return new SourceFreeJava(getExpr(array) + "[" + index + "]");
  }

  @Override
  public FreeJava checkcast(@NotNull FreeJava obj, @NotNull ClassDesc as) {
    return new SourceFreeJava("((" + toClassRef(as) + ")" + getExpr(obj) + ")");
  }
}

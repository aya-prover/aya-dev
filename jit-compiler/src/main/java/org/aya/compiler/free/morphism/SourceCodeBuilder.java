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
  public static @NotNull String getExpr(@NotNull FreeJavaExpr expr) {
    return ((SourceFreeJavaExpr) expr).expr();
  }

  public static @NotNull String getExpr(@NotNull LocalVariable expr) {
    return ((SourceFreeJavaExpr) expr).expr();
  }

  @Override public @NotNull FreeExprBuilder exprBuilder() { return this; }
  @Override public @NotNull FreeJavaResolver resolver() { return parent; }
  @Override
  public @NotNull FreeClassBuilder currentClass() {
    return parent;
  }

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
    buildIf(getExpr(lhs) + " instanceof " + toClassRef(rhs) + name,
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
}

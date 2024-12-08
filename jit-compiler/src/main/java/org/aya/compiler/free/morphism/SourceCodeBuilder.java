// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.compiler.ExprializeUtils;
import org.aya.compiler.SourceBuilder;
import org.aya.compiler.free.ArgumentProvider;
import org.aya.compiler.free.FreeJava;
import org.aya.compiler.free.FreeJavaBuilder;
import org.aya.compiler.free.data.FieldData;
import org.aya.compiler.free.data.LocalVariable;
import org.aya.compiler.free.data.MethodData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.*;

import static org.aya.compiler.free.morphism.SourceFreeJavaBuilder.toClassRef;

public record SourceCodeBuilder(@NotNull ClassDesc owner,
                                @NotNull SourceBuilder sourceBuilder) implements FreeJavaBuilder.CodeBuilder {
  public static @NotNull String toArgs(@NotNull ImmutableSeq<FreeJava> args) {
    return args.view().map(x -> ((SourceFreeJava) x).expr()).joinToString(", ");
  }

  public static @NotNull String getExpr(@NotNull FreeJava expr) {
    return ((SourceFreeJava) expr).expr();
  }

  @Override
  public @NotNull SourceFreeJava makeVar(@NotNull ClassDesc type, @Nullable FreeJava initializer) {
    var mInitializer = initializer == null ? null : ((SourceFreeJava) initializer).expr();
    var name = sourceBuilder.nameGen().nextName();

    sourceBuilder.buildLocalVar(toClassRef(type), name, mInitializer);
    return new SourceFreeJava(name);
  }

  @Override
  public void ifNotTrue(
    @NotNull FreeJava notTrue,
    @NotNull Consumer<FreeJavaBuilder.CodeBuilder> thenBlock,
    @Nullable Consumer<FreeJavaBuilder.CodeBuilder> elseBlock
  ) {
    buildIf("! (" + getExpr(notTrue) + ")", thenBlock, elseBlock);
  }

  private void buildIf(
    @NotNull String condition,
    @NotNull Consumer<FreeJavaBuilder.CodeBuilder> thenBlock,
    @Nullable Consumer<FreeJavaBuilder.CodeBuilder> elseBlock
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
    @NotNull Consumer<FreeJavaBuilder.CodeBuilder> thenBlock,
    @Nullable Consumer<FreeJavaBuilder.CodeBuilder> elseBlock
  ) {
    buildIf(getExpr(theTrue), thenBlock, elseBlock);
  }

  @Override
  public void ifInstanceOf(
    @NotNull FreeJava lhs,
    @NotNull ClassDesc rhs,
    @NotNull BiConsumer<FreeJavaBuilder.CodeBuilder, LocalVariable> thenBlock,
    @Nullable Consumer<FreeJavaBuilder.CodeBuilder> elseBlock
  ) {
    var name = sourceBuilder.nameGen().nextName();
    buildIf(getExpr(lhs) + " instanceof " + toClassRef(rhs) + name,
      cb -> thenBlock.accept(cb, new SourceFreeJava(name)),
      elseBlock);
  }

  @Override
  public void ifNull(
    @NotNull FreeJava isNull,
    @NotNull Consumer<FreeJavaBuilder.CodeBuilder> thenBlock,
    @Nullable Consumer<FreeJavaBuilder.CodeBuilder> elseBlock
  ) {
    buildIf(ExprializeUtils.isNull(getExpr(isNull)), thenBlock, elseBlock);
  }

  @Override
  public void breakable(@NotNull Consumer<FreeJavaBuilder.CodeBuilder> innerBlock) {
    sourceBuilder.appendLine("do {");
    sourceBuilder.runInside(() -> innerBlock.accept(this));
    sourceBuilder.appendLine("} while (false);");
  }

  @Override
  public void breakOut() {
    sourceBuilder.buildBreak();
  }

  @Override
  public void switchCase(
    @NotNull FreeJava elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<FreeJavaBuilder.CodeBuilder> branch,
    @NotNull Consumer<FreeJavaBuilder.CodeBuilder> defaultCase
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
  public @NotNull FreeJava newObject(@NotNull ClassDesc className, @NotNull ImmutableSeq<FreeJava> args) {
    return new SourceFreeJava(ExprializeUtils.makeNew(toClassRef(className), toArgs(args)));
  }

  @Override
  public @NotNull FreeJava refVar(@NotNull LocalVariable name) {
    return (SourceFreeJava) name;
  }

  @Override
  public @NotNull FreeJava invoke(@NotNull MethodData method, @NotNull FreeJava owner, @NotNull ImmutableSeq<FreeJava> args) {
    return new SourceFreeJava(getExpr(owner) + "." + method.name() + "(" + toArgs(args) + ")");
  }

  @Override
  public @NotNull FreeJava invoke(@NotNull MethodData method, @NotNull ImmutableSeq<FreeJava> args) {
    return new SourceFreeJava(toClassRef(method.owner()) + "." + method.name() + "(" + toArgs(args) + ")");
  }

  @Override
  public @NotNull FreeJava refField(@NotNull FieldData field) {
    return new SourceFreeJava(toClassRef(field.owner()) + "." + field.name());
  }

  @Override
  public @NotNull FreeJava refField(@NotNull FieldData field, @NotNull FreeJava owner) {
    return new SourceFreeJava(getExpr(owner) + "." + field.name());
  }

  @Override
  public @NotNull FreeJava refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) {
    return new SourceFreeJava(toClassRef(enumClass) + "." + enumName);
  }

  // We just hope user will not pass non-variable captures
  @Override
  public @NotNull FreeJava mkLambda(
    @NotNull ImmutableSeq<FreeJava> captures,
    @NotNull MethodData method,
    @NotNull Function<ArgumentProvider.Lambda, FreeJava> builder
  ) {
    var name = ImmutableSeq.fill(method.paramTypes().size(), _ ->
      sourceBuilder.nameGen().nextName());
    var ap = new SourceArgumentProvider.Lambda(captures.map(SourceCodeBuilder::getExpr), name);
    return new SourceFreeJava("(" + name.joinToString(", ") + ") -> " + builder.apply(ap));
  }
}

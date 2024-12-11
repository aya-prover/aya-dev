// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.ExprializeUtils;
import org.aya.compiler.SourceBuilder;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.LocalVariable;
import org.aya.compiler.free.data.MethodRef;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.function.Function;

import static org.aya.compiler.free.morphism.SourceCodeBuilder.getExpr;
import static org.aya.compiler.free.morphism.SourceFreeJavaBuilder.toClassRef;

public record SourceFreeJavaBase(SourceBuilder sourceBuilder) implements FreeExprBuilder, FreeJavaResolver {
  public static @NotNull String toArgs(@NotNull ImmutableSeq<FreeJavaExpr> args) {
    return args.view().map(x -> ((SourceFreeJavaExpr) x).expr()).joinToString(", ");
  }

  @Override
  public @NotNull FreeJavaResolver resolver() {
    return this;
  }

  @Override
  public @NotNull MethodRef resolve(
    @NotNull ClassDesc owner,
    @NotNull String name,
    @NotNull ClassDesc returnType,
    @NotNull ImmutableSeq<ClassDesc> paramType,
    boolean isInterface
  ) {
    return new MethodRef.Default(owner, name, returnType, paramType, isInterface);
  }

  @Override
  public @NotNull FieldRef resolve(@NotNull ClassDesc owner, @NotNull String name, @NotNull ClassDesc returnType) {
    return new FieldRef.Default(owner, returnType, name);
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

  @Override
  public @NotNull FreeJavaExpr refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) {
    return new SourceFreeJavaExpr(toClassRef(enumClass) + "." + enumName);
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

  @Override
  public @NotNull FreeJavaExpr mkArray(@NotNull ClassDesc type, int length, @NotNull ImmutableSeq<FreeJavaExpr> initializer) {
    assert initializer.isEmpty() || initializer.sizeEquals(length);
    var init = initializer.isEmpty() ? "" : "{" + toArgs(initializer) + "}";
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

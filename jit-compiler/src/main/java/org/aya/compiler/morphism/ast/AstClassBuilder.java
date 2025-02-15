// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.*;
import org.aya.syntax.compile.AyaMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public record AstClassBuilder(
  @Nullable AyaMetadata metadata,
  @NotNull ClassDesc parentOrThis,
  @Nullable String nested,
  @NotNull Class<?> superclass,
  @NotNull FreezableMutableList<AstDecl> members
) implements ClassBuilder {
  public @NotNull AstDecl.Clazz build() {
    return new AstDecl.Clazz(metadata, parentOrThis, nested, superclass, members.freeze());
  }

  public @NotNull ClassDesc className() {
    return nested == null ? parentOrThis : parentOrThis.nested(nested);
  }

  @Override public void buildNestedClass(
    @NotNull AyaMetadata ayaMetadata,
    @NotNull String name,
    @NotNull Class<?> superclass,
    @NotNull Consumer<ClassBuilder> builder
  ) {
    var classBuilder = new AstClassBuilder(ayaMetadata, className(), name, superclass, FreezableMutableList.create());
    builder.accept(classBuilder);
    members.append(classBuilder.build());
  }

  private void buildMethod(
    @NotNull MethodRef ref,
    @NotNull BiConsumer<ArgumentProvider, CodeBuilder> builder
  ) {
    var codeBuilder = new AstCodeBuilder(FreezableMutableList.create(), new VariablePool(), ref.isConstructor(), false);
    builder.accept(new AstArgumentProvider(ref.paramTypes().size()), codeBuilder);
    members.append(new AstDecl.Method(ref, codeBuilder.build()));
  }

  @Override public @NotNull MethodRef buildMethod(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, CodeBuilder> builder
  ) {
    var ref = new MethodRef(className(), name, returnType, paramTypes, false);
    buildMethod(ref, builder);
    return ref;
  }

  @Override public @NotNull MethodRef buildConstructor(
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, CodeBuilder> builder
  ) {
    var ref = ClassBuilder.makeConstructorRef(className(), paramTypes);
    buildMethod(ref, builder);
    return ref;
  }

  @Override public @NotNull FieldRef buildConstantField(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull Function<ExprBuilder, JavaExpr> initializer
  ) {
    var ref = new FieldRef(className(), returnType, name);
    var expr = (AstExpr) initializer.apply(AstExprBuilder.INSTANCE);
    members.append(new AstDecl.ConstantField(ref, expr));
    return ref;
  }
}

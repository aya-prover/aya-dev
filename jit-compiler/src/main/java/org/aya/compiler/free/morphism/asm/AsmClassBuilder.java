// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.asm;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.MethodRef;
import org.aya.compiler.free.morphism.free.VariablePool;
import org.aya.syntax.compile.CompiledAya;
import org.glavo.classfile.AccessFlag;
import org.glavo.classfile.AccessFlags;
import org.glavo.classfile.ClassBuilder;
import org.glavo.classfile.attribute.NestMembersAttribute;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AsmClassBuilder implements FreeClassBuilder {
  public final @NotNull ClassDesc owner;
  public final @NotNull ClassDesc ownerSuper;
  public final @NotNull ClassBuilder writer;
  public final @NotNull AsmOutputCollector collector;
  public final @NotNull MutableList<ClassDesc> nestedMembers = MutableList.create();
  public final @NotNull MutableMap<FieldRef, Function<FreeExprBuilder, FreeJavaExpr>> fieldInitializers = MutableLinkedHashMap.of();

  public AsmClassBuilder(
    @NotNull ClassDesc owner,
    @NotNull ClassDesc ownerSuper,
    @NotNull ClassBuilder writer,
    @NotNull AsmOutputCollector collector
  ) {
    this.owner = owner;
    this.ownerSuper = ownerSuper;
    this.writer = writer;
    this.collector = collector;
  }

  @Override
  public void buildNestedClass(@NotNull CompiledAya compiledAya, @NotNull String name, @NotNull Class<?> superclass, @NotNull Consumer<FreeClassBuilder> builder) {
    var className = AsmJavaBuilder.buildClass(collector, compiledAya, owner, name, FreeUtil.fromClass(superclass), builder);
    nestedMembers.append(className);
  }

  @Override
  public @NotNull MethodRef buildMethod(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  ) {
    var desc = MethodTypeDesc.of(returnType, paramTypes.asJava());
    writer.withMethod(name, desc, AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.FINAL).flagsMask(), mBuilder -> {
      var ap = new AsmArgumentProvider(paramTypes, false);
      mBuilder.withCode(cb -> {
        var acb = new AsmCodeBuilder(cb, owner, ownerSuper, new VariablePool(paramTypes.size() + 1), null, true);
        builder.accept(ap, acb);
      });
    });

    return new MethodRef(owner, name, returnType, paramTypes, false);
  }

  @Override
  public @NotNull MethodRef buildConstructor(@NotNull ImmutableSeq<ClassDesc> paramTypes, @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder) {
    return buildMethod(ConstantDescs.CD_void, ConstantDescs.INIT_NAME, paramTypes, builder);
  }

  @Override
  public @NotNull FieldRef buildConstantField(@NotNull ClassDesc returnType, @NotNull String name, @NotNull Function<FreeExprBuilder, FreeJavaExpr> initializer) {
    writer.withField(name, returnType, AccessFlags.ofField(AccessFlag.PUBLIC, AccessFlag.STATIC, AccessFlag.FINAL).flagsMask());

    var ref = new FieldRef(owner, returnType, name);
    fieldInitializers.put(ref, initializer);
    return ref;
  }

  public void postBuild() {
    if (nestedMembers.isNotEmpty()) {
      writer.with(NestMembersAttribute.of(nestedMembers.map(x -> writer.constantPool().classEntry(x)).asJava()));
    }

    if (fieldInitializers.isNotEmpty()) {
      writer.withMethodBody(ConstantDescs.CLASS_INIT_NAME, ConstantDescs.MTD_void, AccessFlags.ofMethod(AccessFlag.STATIC).flagsMask(), cb -> {
        var acb = new AsmCodeBuilder(cb, owner, ownerSuper, new VariablePool(), null, false);
        fieldInitializers.forEach((fieldRef, init) -> {
          var expr = init.apply(acb);
          acb.loadExpr(expr);
          cb.putstatic(fieldRef.owner(), fieldRef.name(), fieldRef.returnType());
        });
      });
    }
  }
}

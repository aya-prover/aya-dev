// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.asm;

import java.lang.constant.*;
import java.lang.invoke.LambdaMetafactory;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.value.LazyValue;
import org.aya.compiler.AsmOutputCollector;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.MethodRef;
import org.aya.syntax.compile.CompiledAya;
import org.glavo.classfile.AccessFlag;
import org.glavo.classfile.AccessFlags;
import org.glavo.classfile.ClassBuilder;
import org.glavo.classfile.attribute.InnerClassInfo;
import org.glavo.classfile.attribute.InnerClassesAttribute;
import org.glavo.classfile.attribute.NestMembersAttribute;
import org.glavo.classfile.constantpool.InvokeDynamicEntry;
import org.glavo.classfile.constantpool.MethodHandleEntry;
import org.jetbrains.annotations.NotNull;

public final class AsmClassBuilder implements FreeClassBuilder, AutoCloseable {
  public final @NotNull ClassData classData;
  public final @NotNull ClassBuilder writer;
  public final @NotNull AsmOutputCollector collector;
  public final @NotNull MutableList<String> nestedMembers = MutableList.create();
  public final @NotNull MutableMap<FieldRef, Function<FreeExprBuilder, FreeJavaExpr>> fieldInitializers = MutableLinkedHashMap.of();
  private int lambdaCounter = 0;

  /// @see java.lang.invoke.LambdaMetafactory#metafactory
  private final @NotNull LazyValue<MethodHandleEntry> lambdaBoostrapMethodHandle;

  public AsmClassBuilder(
    @NotNull ClassData classData,
    @NotNull ClassBuilder writer,
    @NotNull AsmOutputCollector collector
  ) {
    this.classData = classData;
    this.writer = writer;
    this.collector = collector;
    this.lambdaBoostrapMethodHandle = LazyValue.of(() -> writer.constantPool().methodHandleEntry(MethodHandleDesc.ofMethod(
      DirectMethodHandleDesc.Kind.STATIC,
      FreeUtil.fromClass(LambdaMetafactory.class),
      "metafactory",
      MethodTypeDesc.of(
        ConstantDescs.CD_CallSite,
        ConstantDescs.CD_MethodHandles_Lookup,
        ConstantDescs.CD_String,
        ConstantDescs.CD_MethodType,
        ConstantDescs.CD_MethodType,
        ConstantDescs.CD_MethodHandle,
        ConstantDescs.CD_MethodType
      )
    )));
  }

  public @NotNull ClassDesc owner() { return classData.className(); }
  public @NotNull ClassDesc ownerSuper() { return classData.classSuper(); }

  @Override public void
  buildNestedClass(@NotNull CompiledAya compiledAya, @NotNull String name, @NotNull Class<?> superclass, @NotNull Consumer<FreeClassBuilder> builder) {
    AsmJavaBuilder.buildClass(collector, compiledAya,
      new ClassData(owner().nested(name), FreeUtil.fromClass(superclass),
        new ClassData.Outer(classData, name)),
      builder);
    nestedMembers.append(name);
  }

  public @NotNull MethodRef buildMethod(
    @NotNull String name,
    @NotNull AccessFlags flags,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull ClassDesc returnType,
    @NotNull BiConsumer<AsmArgumentProvider, AsmCodeBuilder> builder
  ) {
    var desc = MethodTypeDesc.of(returnType, paramTypes.asJava());
    writer.withMethod(name, desc, flags.flagsMask(), mBuilder -> {
      var ap = new AsmArgumentProvider(paramTypes, false);
      mBuilder.withCode(cb -> {
        try (var acb = new AsmCodeBuilder(cb, this, paramTypes, true)) {
          builder.accept(ap, acb);
        }
      });
    });

    return new MethodRef(owner(), name, returnType, paramTypes, false);
  }

  @Override public @NotNull MethodRef buildMethod(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  ) {
    return buildMethod(name, AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.FINAL), paramTypes, returnType, builder::accept);
  }

  @Override
  public @NotNull MethodRef buildConstructor(@NotNull ImmutableSeq<ClassDesc> paramTypes, @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder) {
    return buildMethod(ConstantDescs.INIT_NAME, AccessFlags.ofMethod(AccessFlag.PUBLIC), paramTypes, ConstantDescs.CD_void, (ap, cb) -> {
      builder.accept(ap, cb);
      cb.writer().return_();
    });
  }

  @Override
  public @NotNull FieldRef buildConstantField(@NotNull ClassDesc returnType, @NotNull String name, @NotNull Function<FreeExprBuilder, FreeJavaExpr> initializer) {
    writer.withField(name, returnType, AccessFlags.ofField(AccessFlag.PUBLIC, AccessFlag.STATIC, AccessFlag.FINAL).flagsMask());

    var ref = new FieldRef(owner(), returnType, name);
    fieldInitializers.put(ref, initializer);
    return ref;
  }

  public @NotNull InvokeDynamicEntry makeLambda(
    @NotNull ImmutableSeq<ClassDesc> captureTypes,
    @NotNull MethodRef ref,
    @NotNull BiConsumer<ArgumentProvider.Lambda, FreeCodeBuilder> builder
  ) {
    var pool = writer.constantPool();
    var lambdaMethodName = "lambda$" + lambdaCounter++;
    var fullParams = captureTypes.appendedAll(ref.paramTypes());

    // the method type descriptor to the only abstract method of the functional interface
    var interfaceMethodDesc = MethodTypeDesc.of(ref.returnType(), ref.paramTypes().asJava());
    var lambdaMethodDesc = MethodTypeDesc.of(ref.returnType(), fullParams.asJava());

    // create static method for lambda implementation
    writer.withMethodBody(lambdaMethodName, lambdaMethodDesc, AccessFlags.ofMethod(AccessFlag.PRIVATE, AccessFlag.SYNTHETIC, AccessFlag.STATIC).flagsMask(), cb -> {
      var apl = new AsmArgumentProvider.Lambda(captureTypes, ref.paramTypes());
      try (var acb = new AsmCodeBuilder(cb, this, fullParams, false)) {
        builder.accept(apl, acb);
      }
    });

    // the method handle to the static lambda method
    var lambdaMethodHandle = MethodHandleDesc.ofMethod(
      DirectMethodHandleDesc.Kind.STATIC,
      owner(),
      lambdaMethodName,
      lambdaMethodDesc
    );

    // name: the only abstract method of functional interface
    // type: capture types -> functional interface
    var nameAndType = pool.nameAndTypeEntry(ref.name(), MethodTypeDesc.of(ref.owner(), captureTypes.asJava()));

    // 0th: function signature with type parameters erased
    // 1st: the function name to the lambda
    // 2nd: function signature with type parameters substituted
    var bsmEntry = pool.bsmEntry(lambdaBoostrapMethodHandle.get(), ImmutableSeq.of(
      interfaceMethodDesc,
      lambdaMethodHandle,
      interfaceMethodDesc   // just hope it doesn't cause any trouble
    ).map(pool::loadableConstantEntry).asJava());

    return pool.invokeDynamicEntry(bsmEntry, nameAndType);
  }

  @Override
  public void close() {
    ImmutableSeq<InnerClassInfo> innerClassesEntries;
    var outerClassData = classData.outer();
    if (outerClassData == null) {
      innerClassesEntries = nestedMembers.map(cd ->
        InnerClassInfo.of(owner().nested(cd), Optional.of(owner()), Optional.of(cd), ClassData.AF_NESTED));
    } else {
      assert nestedMembers.isEmpty();
      innerClassesEntries = ImmutableSeq.of(InnerClassInfo.of(owner(), Optional.of(outerClassData.data().classSuper()),
        Optional.of(outerClassData.thisName()), ClassData.AF_NESTED));
    }

    if (innerClassesEntries.isNotEmpty()) {
      writer.with(InnerClassesAttribute.of(innerClassesEntries.asJava()));
    }

    if (nestedMembers.isNotEmpty()) {
      var pool = writer.constantPool();
      writer.with(NestMembersAttribute.of(nestedMembers.map(t -> pool.classEntry(owner().nested(t))).asJava()));
    }

    if (fieldInitializers.isNotEmpty()) {
      writer.withMethodBody(ConstantDescs.CLASS_INIT_NAME, ConstantDescs.MTD_void, AccessFlags.ofMethod(AccessFlag.STATIC).flagsMask(), cb -> {
        try (var acb = new AsmCodeBuilder(cb, this, ImmutableSeq.empty(), false)) {
          fieldInitializers.forEach((fieldRef, init) -> {
            var expr = init.apply(acb);
            acb.loadExpr(expr);
            cb.putstatic(fieldRef.owner(), fieldRef.name(), fieldRef.returnType());
          });
          cb.return_();
        }
      });
    }
  }
}

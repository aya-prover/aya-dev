// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.asm;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.compiler.FieldRef;
import org.aya.compiler.LocalVariable;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.ArgumentProvider;
import org.aya.compiler.morphism.FreeJavaResolver;
import org.aya.compiler.morphism.JavaExpr;
import org.aya.util.Panic;
import org.glavo.classfile.Label;
import org.glavo.classfile.Opcode;
import org.glavo.classfile.TypeKind;
import org.glavo.classfile.instruction.SwitchCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

/// @param breaking the label that used for jumping out
/// @param hasThis  is this an instance method or a static method
public record AsmCodeBuilder(
  @NotNull org.glavo.classfile.CodeBuilder writer,
  @NotNull AsmClassBuilder parent,
  @NotNull AsmVariablePool pool,
  @Nullable Label breaking,
  @Nullable Label continuing,
  boolean hasThis
) implements AutoCloseable {
  public static final @NotNull AsmExpr ja = AsmExpr.withType(ConstantDescs.CD_boolean, builder -> builder.writer.iconst_1());
  public static final @NotNull AsmExpr nein = AsmExpr.withType(ConstantDescs.CD_boolean, builder -> builder.writer.iconst_0());

  public AsmCodeBuilder(
    @NotNull org.glavo.classfile.CodeBuilder writer,
    @NotNull AsmClassBuilder parent,
    @NotNull ImmutableSeq<ClassDesc> parameterTypes,
    boolean hasThis
  ) {
    this(writer, parent,
      AsmVariablePool.from(hasThis ? parent.owner() : null, parameterTypes),
      null,
      null,
      hasThis
    );
  }

  public @NotNull AsmVariable assertVar(@NotNull LocalVariable var) { return (AsmVariable) var; }
  public @NotNull AsmExpr assertExpr(@NotNull JavaExpr expr) { return (AsmExpr) expr; }
  public void loadVar(@NotNull LocalVariable var) {
    var asmVar = assertVar(var);
    writer.loadInstruction(asmVar.kind(), asmVar.slot());
  }

  public void loadExpr(@NotNull JavaExpr expr) { assertExpr(expr).accept(this); }
  public void close() { pool.submit(this); }

  public void subscoped(@NotNull org.glavo.classfile.CodeBuilder innerWriter, @Nullable Label breaking, @Nullable Label continuing, @NotNull Consumer<AsmCodeBuilder> block) {
    try (var innerBuilder = new AsmCodeBuilder(innerWriter, parent, pool.subscope(), breaking, continuing, hasThis)) {
      block.accept(innerBuilder);
    }
  }

  public void subscoped(@NotNull org.glavo.classfile.CodeBuilder innerWrite, @NotNull Consumer<AsmCodeBuilder> block) {
    subscoped(innerWrite, breaking, continuing, block);
  }

  public void subscoped(@NotNull Consumer<AsmCodeBuilder> block) { subscoped(writer, breaking, continuing, block); }

  public @NotNull AsmVariable makeVar(@NotNull ClassDesc type, @Nullable JavaExpr initializer) {
    var variable = pool.acquire(type);
    if (initializer != null) updateVar(variable, initializer);
    return variable;
  }

  public void invokeSuperCon(@NotNull ImmutableSeq<ClassDesc> superConParams, @NotNull ImmutableSeq<JavaExpr> superConArgs) {
    invoke(
      InvokeKind.Special,
      FreeJavaResolver.resolve(parent.ownerSuper(), ConstantDescs.INIT_NAME, ConstantDescs.CD_void, superConParams, false),
      thisRef(),
      superConArgs);
  }

  public void updateVar(@NotNull LocalVariable var, @NotNull JavaExpr update) {
    var asmVar = assertVar(var);
    var expr = assertExpr(update);
    expr.accept(this);
    writer.storeInstruction(asmVar.kind(), asmVar.slot());
  }

  public void updateArray(@NotNull JavaExpr array, int idx, @NotNull JavaExpr update) {
    var expr = assertExpr(array);
    var component = expr.type().componentType();
    assert component != null;     // null if non-array, which is unacceptable
    var kind = TypeKind.fromDescriptor(component.descriptorString());

    expr.accept(this);
    iconst(idx).accept(this);
    loadExpr(update);
    writer.arrayStoreInstruction(kind);
  }

  public void ifThenElse(@NotNull Opcode code, @NotNull Consumer<AsmCodeBuilder> thenBlock, @Nullable Consumer<AsmCodeBuilder> elseBlock) {
    if (elseBlock != null) {
      writer.ifThenElse(code,
        builder -> subscoped(builder, thenBlock),
        builder -> subscoped(builder, elseBlock));
    } else {
      writer.ifThen(code, builder -> subscoped(builder, thenBlock));
    }
  }

  public void ifNotTrue(@NotNull LocalVariable notTrue, @NotNull Consumer<AsmCodeBuilder> thenBlock, @Nullable Consumer<AsmCodeBuilder> elseBlock) {
    loadVar(notTrue);
    ifThenElse(Opcode.IFEQ, thenBlock, elseBlock);
  }

  public void ifTrue(@NotNull LocalVariable theTrue, @NotNull Consumer<AsmCodeBuilder> thenBlock, @Nullable Consumer<AsmCodeBuilder> elseBlock) {
    loadVar(theTrue);
    ifThenElse(Opcode.IFNE, thenBlock, elseBlock);
  }

  public void ifInstanceOf(@NotNull JavaExpr lhs, @NotNull ClassDesc rhs, @NotNull BiConsumer<AsmCodeBuilder, LocalVariable> thenBlock, @Nullable Consumer<AsmCodeBuilder> elseBlock) {
    var lhsExpr = assertExpr(lhs);
    lhsExpr.accept(this);
    writer.instanceof_(rhs);
    ifThenElse(Opcode.IFNE, builder -> {
      var cast = builder.checkcast(lhs, rhs);
      var bind = builder.makeVar(rhs, cast);
      thenBlock.accept(builder, bind);
    }, elseBlock);
  }

  public void ifIntEqual(@NotNull JavaExpr lhs, int rhs, @NotNull Consumer<AsmCodeBuilder> thenBlock, @Nullable Consumer<AsmCodeBuilder> elseBlock) {
    loadExpr(lhs);
    loadExpr(iconst(rhs));
    ifThenElse(Opcode.IF_ICMPEQ, thenBlock, elseBlock);
  }

  public void ifRefEqual(@NotNull JavaExpr lhs, @NotNull JavaExpr rhs, @NotNull Consumer<AsmCodeBuilder> thenBlock, @Nullable Consumer<AsmCodeBuilder> elseBlock) {
    loadExpr(lhs);
    loadExpr(rhs);
    ifThenElse(Opcode.IF_ACMPEQ, thenBlock, elseBlock);
  }

  public void ifNull(@NotNull JavaExpr isNull, @NotNull Consumer<AsmCodeBuilder> thenBlock, @Nullable Consumer<AsmCodeBuilder> elseBlock) {
    loadExpr(isNull);
    ifThenElse(Opcode.IFNULL, thenBlock, elseBlock);
  }

  public void breakable(@NotNull Consumer<AsmCodeBuilder> innerBlock) {
    // sorry, nesting breakable is unsupported.
    if (breaking != null) Panic.unreachable();
    writer.block(builder -> {
      var endLabel = builder.breakLabel();
      subscoped(builder, endLabel, continuing, innerBlock);
    });
  }

  public void breakOut() {
    if (breaking == null) Panic.unreachable();
    writer.goto_(breaking);
  }

  public void whileTrue(@NotNull Consumer<AsmCodeBuilder> innerBlock) {
    if (continuing != null) Panic.unreachable();
    writer.block(builder -> {
      var continueLabel = builder.startLabel();
      subscoped(builder, breaking, continueLabel, innerBlock);
    });
  }

  public void continueLoop() {
    if (continuing == null) Panic.unreachable();
    writer.goto_(continuing);
  }

  public void exec(@NotNull JavaExpr expr) {
    var asmExpr = assertExpr(expr);
    asmExpr.accept(this);
    if (!asmExpr.type().equals(ConstantDescs.CD_void)) {
      writer.pop();
    }
  }

  public void switchCase(
    @NotNull LocalVariable elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<AsmCodeBuilder> branch,
    @NotNull Consumer<AsmCodeBuilder> defaultCase
  ) {
    var switchCases = cases.mapToObj(i -> SwitchCase.of(i, writer.newLabel()));
    var defaultLabel = writer.newLabel();

    loadVar(elim);
    writer.tableswitch(defaultLabel, switchCases.asJava());

    cases.forEach(i ->
      writer.block(inner -> {
        inner.labelBinding(switchCases.get(i).target());
        subscoped(inner, builder -> branch.accept(builder, i));
      })
    );

    writer.labelBinding(defaultLabel);
    subscoped(defaultCase);
  }

  public void returnWith(@NotNull JavaExpr expr) {
    var asmExpr = assertExpr(expr);
    var kind = TypeKind.fromDescriptor(asmExpr.type().descriptorString());
    asmExpr.accept(this);
    writer.returnInstruction(kind);
  }

  public enum InvokeKind {
    Special, Virtual, Static
  }

  public void invoke(
    @NotNull InvokeKind kind,
    @NotNull MethodRef ref,
    @Nullable JavaExpr self,
    @NotNull ImmutableSeq<JavaExpr> args
  ) {
    var owner = ref.owner();
    var name = ref.name();
    var desc = MethodTypeDesc.of(ref.returnType(), ref.paramTypes().asJava());
    var isInterface = ref.isInterface();

    assert (self == null) == (kind == InvokeKind.Static);

    if (self != null) {
      loadExpr(self);
    }

    args.forEach(this::loadExpr);

    switch (kind) {
      case Static -> writer.invokestatic(owner, name, desc, isInterface);
      case Special -> writer.invokespecial(owner, name, desc, isInterface);
      case Virtual -> {
        if (isInterface) {
          writer.invokeinterface(owner, name, desc);
        } else {
          writer.invokevirtual(owner, name, desc);
        }
      }
    }
  }

  public @NotNull AsmExpr mkNew(@NotNull MethodRef conRef, @NotNull ImmutableSeq<JavaExpr> args) {
    return AsmExpr.withType(conRef.owner(), builder -> {
      builder.writer.new_(conRef.owner());
      builder.invoke(
        InvokeKind.Special,
        conRef,
        AsmExpr.withType(conRef.owner(), builder0 -> builder0.writer.dup()),
        args
      );
    });
  }

  public @NotNull AsmExpr invoke(@NotNull MethodRef method, @NotNull JavaExpr owner, @NotNull ImmutableSeq<JavaExpr> args) {
    return AsmExpr.withType(method.returnType(), builder -> builder.invoke(InvokeKind.Virtual, method, owner, args));
  }

  public @NotNull AsmExpr invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<JavaExpr> args) {
    return AsmExpr.withType(method.returnType(), builder ->
      builder.invoke(InvokeKind.Static, method, null, args));
  }

  public @NotNull AsmExpr refField(@NotNull FieldRef field) {
    return AsmExpr.withType(field.returnType(), builder ->
      builder.writer.getstatic(field.owner(), field.name(), field.returnType()));
  }
  public @NotNull AsmExpr refField(@NotNull FieldRef field, @NotNull JavaExpr owner) {
    return AsmExpr.withType(field.returnType(), builder -> {
      builder.loadExpr(owner);
      builder.writer.getfield(field.owner(), field.name(), field.returnType());
    });
  }

  public @NotNull AsmExpr refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) {
    var ref = FreeJavaResolver.resolve(enumClass, enumName, enumClass);
    return refField(ref);
  }

  public @NotNull AsmExpr
  mkLambda(@NotNull ImmutableSeq<JavaExpr> captures, @NotNull MethodRef method, @NotNull BiConsumer<ArgumentProvider.Lambda, AsmCodeBuilder> lamBody) {
    var captureExprs = captures.map(this::assertExpr);
    var captureTypes = captureExprs.map(AsmExpr::type);
    var indy = parent.makeLambda(captureTypes, method, lamBody);

    return AsmExpr.withType(method.owner(), builder -> {
      captureExprs.forEach(t -> t.accept(builder));
      builder.writer.invokedynamic(indy);
    });
  }

  public @NotNull AsmExpr iconst(int i) {
    return AsmExpr.withType(ConstantDescs.CD_int, builder -> {
      switch (i) {
        case -1 -> builder.writer.iconst_m1();
        case 0 -> builder.writer.iconst_0();
        case 1 -> builder.writer.iconst_1();
        case 2 -> builder.writer.iconst_2();
        case 3 -> builder.writer.iconst_3();
        case 4 -> builder.writer.iconst_4();
        case 5 -> builder.writer.iconst_5();
        default -> {
          if (Byte.MIN_VALUE <= i && i <= Byte.MAX_VALUE) builder.writer.bipush(i);
          else if (Short.MIN_VALUE <= i && i <= Short.MAX_VALUE) builder.writer.sipush(i);
          else builder.writer.ldc(builder.writer.constantPool().intEntry(i));
        }
      }
    });
  }

  public @NotNull AsmExpr iconst(boolean b) { return b ? ja : nein; }
  public @NotNull AsmExpr aconst(@NotNull String value) {
    return AsmExpr.withType(ConstantDescs.CD_String, builder ->
      builder.writer.ldc(builder.writer.constantPool().stringEntry(value)));
  }

  public @NotNull AsmExpr aconstNull(@NotNull ClassDesc type) {
    return AsmExpr.withType(type, builder -> builder.writer.aconst_null());
  }

  public @NotNull AsmExpr thisRef() {
    assert hasThis;
    return AsmExpr.withType(parent.owner(), builder -> builder.writer.aload(0));
  }

  public @NotNull AsmExpr mkArray(@NotNull ClassDesc type, int length, @Nullable ImmutableSeq<JavaExpr> initializer) {
    var arrayType = type.arrayType();
    var dup = AsmExpr.withType(arrayType, builder -> builder.writer.dup());

    return AsmExpr.withType(arrayType, builder -> {
      builder.iconst(length).accept(builder);

      var kind = TypeKind.fromDescriptor(type.descriptorString());
      if (kind == TypeKind.ReferenceType) {
        builder.writer.anewarray(type);
      } else {
        builder.writer.newarray(kind);
      }

      if (initializer != null) {
        assert initializer.size() == length;
        initializer.forEachIndexed((i, init) ->
          builder.updateArray(dup, i, init));
      }
    });
  }

  public @NotNull AsmExpr getArray(@NotNull LocalVariable array, int index) {
    var var = assertVar(array);
    var component = var.type().componentType();
    assert component != null;
    var kind = TypeKind.fromDescriptor(component.descriptorString());

    return AsmExpr.withType(component, builder -> {
      builder.loadVar(var);
      builder.iconst(index).accept(builder);
      builder.writer.arrayLoadInstruction(kind);
    });
  }

  public @NotNull AsmExpr checkcast(@NotNull LocalVariable obj, @NotNull ClassDesc as) {
    return AsmExpr.withType(as, builder -> {
      builder.loadVar(obj);
      builder.writer.checkcast(as);
    });
  }
}

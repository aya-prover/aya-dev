// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.control.Option;
import org.aya.api.concrete.ConcreteDecl;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.resolve.context.Context;
import org.aya.core.def.*;
import org.aya.generic.Modifier;
import org.aya.tyck.StmtTycker;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.BiFunction;

/**
 * Concrete definition, corresponding to {@link Def}.
 *
 * @author re-xyr
 */
public sealed abstract class Decl extends Signatured implements Stmt, ConcreteDecl {
  public final @NotNull Accessibility accessibility;
  public final @NotNull ImmutableSeq<Stmt> abuseBlock;
  public @Nullable Context ctx = null;

  @Override public @NotNull Accessibility accessibility() {
    return accessibility;
  }

  protected Decl(
    @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
    @NotNull Accessibility accessibility,
    @NotNull ImmutableSeq<Stmt> abuseBlock,
    @NotNull ImmutableSeq<Expr.Param> telescope
  ) {
    super(sourcePos, entireSourcePos, telescope);
    this.accessibility = accessibility;
    this.abuseBlock = abuseBlock;
  }

  @Contract(pure = true) public abstract @NotNull DefVar<? extends Def, ? extends Decl> ref();

  protected abstract <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  public @NotNull Def tyck(@NotNull Reporter reporter, Trace.@Nullable Builder builder, boolean headerOnly) {
    return new StmtTycker(reporter, builder, headerOnly).tyck(this);
  }

  @Override public final <P, R> R accept(Stmt.@NotNull Visitor<P, R> visitor, P p) {
    return accept((Visitor<? super P, ? extends R>) visitor, p);
  }

  public final <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    visitor.traceEntrance(this, p);
    var ret = doAccept(visitor, p);
    visitor.traceExit(p, ret);
    return ret;
  }

  @ApiStatus.NonExtendable
  public final @Override <P, R> R doAccept(Stmt.@NotNull Visitor<P, R> visitor, P p) {
    return doAccept((Decl.Visitor<P, R>) visitor, p);
  }

  public interface Visitor<P, R> {
    default void traceEntrance(@NotNull Signatured item, P p) {
    }
    default void traceExit(P p, R r) {
    }

    @ApiStatus.NonExtendable
    default <T extends Signatured, RR extends R> RR traced(@NotNull T yeah, P p, @NotNull BiFunction<T, P, RR> f) {
      traceEntrance(yeah, p);
      var r = f.apply(yeah, p);
      traceExit(p, r);
      return r;
    }

    @ApiStatus.OverrideOnly R visitCtor(Decl.@NotNull DataCtor ctor, P p);
    @ApiStatus.OverrideOnly R visitField(Decl.@NotNull StructField field, P p);
    R visitData(Decl.@NotNull DataDecl decl, P p);
    R visitStruct(Decl.@NotNull StructDecl decl, P p);
    R visitFn(Decl.@NotNull FnDecl decl, P p);
    R visitPrim(Decl.@NotNull PrimDecl decl, P p);
  }

  /**
   * @author ice1000
   * @see PrimDef
   */
  public static final class PrimDecl extends Decl implements OpDecl {
    public final @NotNull DefVar<? extends PrimDef, PrimDecl> ref;
    public @Nullable Expr result;
    public final @Nullable Operator operator;

    public PrimDecl(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @Nullable Operator operator,
      @NotNull DefVar<? extends PrimDef, PrimDecl> ref,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @Nullable Expr result
    ) {
      // TODO[ice]: are we sure? Empty abuse block?
      super(sourcePos, entireSourcePos, Accessibility.Public, ImmutableSeq.empty(), telescope);
      this.result = result;
      this.operator = operator;
      ref.concrete = this;
      this.ref = ref;
    }

    @Override public @NotNull DefVar<? extends PrimDef, PrimDecl> ref() {
      return ref;
    }

    @Override protected <P, R> R doAccept(@NotNull Decl.Visitor<P, R> visitor, P p) {
      return visitor.visitPrim(this, p);
    }

    @Override public @Nullable Operator asOperator() {
      return operator;
    }
  }

  public static final class DataCtor extends Signatured implements OpDecl {
    public final @NotNull DefVar<CtorDef, Decl.DataCtor> ref;
    public DefVar<DataDef, DataDecl> dataRef;
    public @NotNull ImmutableSeq<Pattern.Clause> clauses;
    public @NotNull ImmutableSeq<Pattern> patterns;
    public final @Nullable Operator operator;
    public final boolean coerce;

    public DataCtor(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @Nullable Operator operator,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull ImmutableSeq<Pattern.Clause> clauses,
      @NotNull ImmutableSeq<Pattern> patterns,
      boolean coerce
    ) {
      super(sourcePos, entireSourcePos, telescope);
      this.clauses = clauses;
      this.operator = operator;
      this.coerce = coerce;
      this.patterns = patterns;
      this.ref = DefVar.concrete(this, name);
    }

    @Override public @NotNull DefVar<CtorDef, DataCtor> ref() {
      return ref;
    }

    @Override public @Nullable Operator asOperator() {
      return operator;
    }
  }

  /**
   * Concrete data definition
   *
   * @author kiva
   * @see DataDef
   */
  public static final class DataDecl extends Decl implements OpDecl {
    public final @NotNull DefVar<DataDef, DataDecl> ref;
    public @NotNull Expr result;
    public final @NotNull ImmutableSeq<DataCtor> body;
    public @Nullable Operator operator;

    public DataDecl(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @NotNull Accessibility accessibility,
      @Nullable Operator operator,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      @NotNull ImmutableSeq<DataCtor> body,
      @NotNull ImmutableSeq<Stmt> abuseBlock
    ) {
      super(sourcePos, entireSourcePos, accessibility, abuseBlock, telescope);
      this.result = result;
      this.body = body;
      this.operator = operator;
      this.ref = DefVar.concrete(this, name);
      body.forEach(ctors -> ctors.dataRef = ref);
    }

    @Override protected <P, R> R doAccept(@NotNull Decl.Visitor<P, R> visitor, P p) {
      return visitor.visitData(this, p);
    }

    @Override public @NotNull DefVar<DataDef, DataDecl> ref() {
      return this.ref;
    }

    @Override public @Nullable Operator asOperator() {
      return operator;
    }
  }

  /**
   * Concrete structure definition
   *
   * @author vont
   */
  public static final class StructDecl extends Decl implements OpDecl {
    public final @NotNull DefVar<StructDef, StructDecl> ref;
    public @NotNull
    final ImmutableSeq<StructField> fields;
    public final @Nullable Operator operator;
    public @NotNull Expr result;

    public StructDecl(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @NotNull Accessibility accessibility,
      @Nullable Operator operator,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      // @NotNull ImmutableSeq<String> superClassNames,
      @NotNull ImmutableSeq<StructField> fields,
      @NotNull ImmutableSeq<Stmt> abuseBlock
    ) {
      super(sourcePos, entireSourcePos, accessibility, abuseBlock, telescope);
      this.operator = operator;
      this.result = result;
      this.fields = fields;
      this.ref = DefVar.concrete(this, name);
      fields.forEach(field -> field.structRef = ref);
    }

    @Override public @NotNull DefVar<? extends Def, StructDecl> ref() {
      return ref;
    }

    @Override protected <P, R> R doAccept(Decl.@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitStruct(this, p);
    }

    @Override public @Nullable Operator asOperator() {
      return operator;
    }
  }

  public static final class StructField extends Signatured {
    public final @NotNull DefVar<FieldDef, Decl.StructField> ref;
    public DefVar<StructDef, StructDecl> structRef;
    public @NotNull ImmutableSeq<Pattern.Clause> clauses;
    public @NotNull Expr result;
    public @NotNull Option<Expr> body;

    public final boolean coerce;

    public StructField(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      @NotNull Option<Expr> body,
      @NotNull ImmutableSeq<Pattern.Clause> clauses,
      boolean coerce
    ) {
      super(sourcePos, entireSourcePos, telescope);
      this.coerce = coerce;
      this.result = result;
      this.clauses = clauses;
      this.body = body;
      this.ref = DefVar.concrete(this, name);
    }

    @Override public @NotNull DefVar<? extends Def, StructField> ref() {
      return ref;
    }
  }

  /**
   * Concrete function definition
   *
   * @author re-xyr
   * @see FnDef
   */
  public static final class FnDecl extends Decl implements OpDecl {
    public final @NotNull EnumSet<Modifier> modifiers;
    public final @Nullable Operator operator;
    public final @NotNull DefVar<FnDef, FnDecl> ref;
    public @NotNull Expr result;
    public @NotNull Either<Expr, ImmutableSeq<Pattern.Clause>> body;

    public FnDecl(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @NotNull Accessibility accessibility,
      @NotNull EnumSet<Modifier> modifiers,
      @Nullable Operator operator,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      @NotNull Either<Expr, ImmutableSeq<Pattern.Clause>> body,
      @NotNull ImmutableSeq<Stmt> abuseBlock
    ) {
      super(sourcePos, entireSourcePos, accessibility, abuseBlock, telescope);
      this.modifiers = modifiers;
      this.operator = operator;
      this.ref = DefVar.concrete(this, name);
      this.result = result;
      this.body = body;
    }

    @Override protected <P, R> R doAccept(@NotNull Decl.Visitor<P, R> visitor, P p) {
      return visitor.visitFn(this, p);
    }

    @Override public @NotNull DefVar<FnDef, FnDecl> ref() {
      return this.ref;
    }

    @Override public @Nullable Operator asOperator() {
      return operator;
    }
  }
}

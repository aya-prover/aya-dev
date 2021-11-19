// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.control.Option;
import org.aya.api.concrete.ConcreteDecl;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.resolve.context.Context;
import org.aya.core.def.*;
import org.aya.core.term.Term;
import org.aya.generic.Modifier;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
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
  public @Nullable Context ctx = null;

  @Override public @NotNull Accessibility accessibility() {
    return accessibility;
  }

  protected Decl(
    @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
    @NotNull Accessibility accessibility,
    @NotNull ImmutableSeq<Expr.Param> telescope
  ) {
    super(sourcePos, entireSourcePos, telescope);
    this.accessibility = accessibility;
  }

  @Contract(pure = true) public abstract @NotNull DefVar<? extends Def, ? extends Decl> ref();

  protected abstract <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

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
    public final @NotNull DefVar<PrimDef, PrimDecl> ref;
    public @NotNull Expr result;
    public final @Nullable OpDecl.OpInfo opInfo;

    public PrimDecl(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @Nullable OpDecl.OpInfo opInfo,
      @NotNull DefVar<PrimDef, PrimDecl> ref,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result
    ) {
      super(sourcePos, entireSourcePos, Accessibility.Public, telescope);
      this.result = result;
      this.opInfo = opInfo;
      ref.concrete = this;
      this.ref = ref;
    }

    @Override public @NotNull DefVar<PrimDef, PrimDecl> ref() {
      return ref;
    }

    @Override protected <P, R> R doAccept(@NotNull Decl.Visitor<P, R> visitor, P p) {
      return visitor.visitPrim(this, p);
    }

    @Override public @Nullable OpDecl.OpInfo opInfo() {
      return opInfo;
    }
  }

  public static final class DataCtor extends Signatured implements OpDecl {
    public final @NotNull DefVar<CtorDef, Decl.DataCtor> ref;
    public DefVar<DataDef, DataDecl> dataRef;
    /** Similar to {@link Signatured#signature}, but stores the bindings in {@link DataCtor#patterns} */
    public ImmutableSeq<Term.Param> patternTele;
    public @NotNull ImmutableSeq<Pattern.Clause> clauses;
    public @NotNull ImmutableSeq<Pattern> patterns;
    public final @Nullable OpDecl.OpInfo opInfo;
    public final @NotNull BindBlock bindBlock;
    public final boolean coerce;

    public DataCtor(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @Nullable OpDecl.OpInfo opInfo,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull ImmutableSeq<Pattern.Clause> clauses,
      @NotNull ImmutableSeq<Pattern> patterns,
      boolean coerce,
      @NotNull BindBlock bindBlock
    ) {
      super(sourcePos, entireSourcePos, telescope);
      this.clauses = clauses;
      this.opInfo = opInfo;
      this.coerce = coerce;
      this.patterns = patterns;
      this.ref = DefVar.concrete(this, name);
      this.bindBlock = bindBlock;
    }

    @Override public @NotNull DefVar<CtorDef, DataCtor> ref() {
      return ref;
    }

    @Override public @Nullable OpDecl.OpInfo opInfo() {
      return opInfo;
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
    public final @Nullable OpDecl.OpInfo opInfo;
    public final @NotNull BindBlock bindBlock;

    public DataDecl(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @NotNull Accessibility accessibility,
      @Nullable OpDecl.OpInfo opInfo,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      @NotNull ImmutableSeq<DataCtor> body,
      @NotNull BindBlock bindBlock
    ) {
      super(sourcePos, entireSourcePos, accessibility, telescope);
      this.result = result;
      this.body = body;
      this.opInfo = opInfo;
      this.ref = DefVar.concrete(this, name);
      this.bindBlock = bindBlock;
      body.forEach(ctors -> ctors.dataRef = ref);
    }

    @Override protected <P, R> R doAccept(@NotNull Decl.Visitor<P, R> visitor, P p) {
      return visitor.visitData(this, p);
    }

    @Override public @NotNull DefVar<DataDef, DataDecl> ref() {
      return this.ref;
    }

    @Override public @Nullable OpDecl.OpInfo opInfo() {
      return opInfo;
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
    public final @Nullable OpDecl.OpInfo opInfo;
    public final @NotNull BindBlock bindBlock;
    public @NotNull Expr result;

    public StructDecl(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @NotNull Accessibility accessibility,
      @Nullable OpDecl.OpInfo opInfo,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      // @NotNull ImmutableSeq<String> superClassNames,
      @NotNull ImmutableSeq<StructField> fields,
      @NotNull BindBlock bindBlock
    ) {
      super(sourcePos, entireSourcePos, accessibility, telescope);
      this.opInfo = opInfo;
      this.result = result;
      this.fields = fields;
      this.ref = DefVar.concrete(this, name);
      this.bindBlock = bindBlock;
      fields.forEach(field -> field.structRef = ref);
    }

    @Override public @NotNull DefVar<StructDef, StructDecl> ref() {
      return ref;
    }

    @Override protected <P, R> R doAccept(Decl.@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitStruct(this, p);
    }

    @Override public @Nullable OpDecl.OpInfo opInfo() {
      return opInfo;
    }
  }

  public static final class StructField extends Signatured implements OpDecl {
    public final @NotNull DefVar<FieldDef, Decl.StructField> ref;
    public DefVar<StructDef, StructDecl> structRef;
    public @NotNull ImmutableSeq<Pattern.Clause> clauses;
    public @NotNull Expr result;
    public final @Nullable OpDecl.OpInfo opInfo;
    public final @NotNull BindBlock bindBlock;
    public @NotNull Option<Expr> body;

    public final boolean coerce;

    public StructField(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @Nullable OpDecl.OpInfo opInfo,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      @NotNull Option<Expr> body,
      @NotNull ImmutableSeq<Pattern.Clause> clauses,
      boolean coerce,
      @NotNull BindBlock bindBlock
    ) {
      super(sourcePos, entireSourcePos, telescope);
      this.coerce = coerce;
      this.result = result;
      this.clauses = clauses;
      this.body = body;
      this.opInfo = opInfo;
      this.ref = DefVar.concrete(this, name);
      this.bindBlock = bindBlock;
    }

    @Override public @NotNull DefVar<FieldDef, StructField> ref() {
      return ref;
    }

    @Override public @Nullable OpDecl.OpInfo opInfo() {
      return opInfo;
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
    public final @Nullable OpDecl.OpInfo opInfo;
    public final @NotNull BindBlock bindBlock;
    public final @NotNull DefVar<FnDef, FnDecl> ref;
    public @NotNull Expr result;
    public @NotNull Either<Expr, ImmutableSeq<Pattern.Clause>> body;

    public FnDecl(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @NotNull Accessibility accessibility,
      @NotNull EnumSet<Modifier> modifiers,
      @Nullable OpDecl.OpInfo opInfo,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      @NotNull Either<Expr, ImmutableSeq<Pattern.Clause>> body,
      @NotNull BindBlock bindBlock
    ) {
      super(sourcePos, entireSourcePos, accessibility, telescope);
      this.modifiers = modifiers;
      this.opInfo = opInfo;
      this.ref = DefVar.concrete(this, name);
      this.result = result;
      this.body = body;
      this.bindBlock = bindBlock;
    }

    @Override protected <P, R> R doAccept(@NotNull Decl.Visitor<P, R> visitor, P p) {
      return visitor.visitFn(this, p);
    }

    @Override public @NotNull DefVar<FnDef, FnDecl> ref() {
      return this.ref;
    }

    @Override public @Nullable OpDecl.OpInfo opInfo() {
      return opInfo;
    }
  }
}

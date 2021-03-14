// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.concrete.def.ConcreteDecl;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.api.util.Assoc;
import org.aya.concrete.resolve.context.Context;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.generic.Modifier;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Either;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * concrete definition, corresponding to {@link Def}.
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
    @NotNull SourcePos sourcePos,
    @NotNull Accessibility accessibility,
    @NotNull ImmutableSeq<Stmt> abuseBlock,
    @NotNull ImmutableSeq<Expr.Param> telescope
  ) {
    super(sourcePos, telescope);
    this.accessibility = accessibility;
    this.abuseBlock = abuseBlock;
  }

  @Contract(pure = true) public abstract @NotNull DefVar<? extends Def, ? extends Decl> ref();

  protected abstract <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  public final <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    visitor.traceEntrance(this, p);
    var ret = doAccept(visitor, p);
    visitor.traceExit(ret);
    return ret;
  }

  @ApiStatus.NonExtendable
  public final @Override <P, R> R doAccept(Stmt.@NotNull Visitor<P, R> visitor, P p) {
    return doAccept((Decl.Visitor<P, R>) visitor, p);
  }

  @ApiStatus.NonExtendable
  public final @Override <P, R> R doAccept(Signatured.@NotNull Visitor<P, R> visitor, P p) {
    return doAccept((Decl.Visitor<P, R>) visitor, p);
  }

  public interface Visitor<P, R> {
    default void traceEntrance(@NotNull Decl decl, P p) {
    }
    default void traceExit(R r) {
    }
    R visitData(Decl.@NotNull DataDecl decl, P p);
    R visitStruct(Decl.@NotNull StructDecl decl, P p);
    R visitFn(Decl.@NotNull FnDecl decl, P p);
  }

  public static final class DataCtor extends Signatured {
    public final @NotNull DefVar<DataDef.Ctor, Decl.DataCtor> ref;
    public DefVar<DataDef, DataDecl> dataRef;
    public @NotNull ImmutableSeq<Pattern.Clause> clauses;
    public boolean coerce;

    public DataCtor(@NotNull SourcePos sourcePos,
                    @NotNull String name,
                    @NotNull ImmutableSeq<Expr.Param> telescope,
                    @NotNull ImmutableSeq<Pattern.Clause> clauses,
                    boolean coerce) {
      super(sourcePos, telescope);
      this.clauses = clauses;
      this.coerce = coerce;
      this.ref = DefVar.concrete(this, name);
    }

    @Override protected <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCtor(this, p);
    }

    @Override public @NotNull DefVar<DataDef.Ctor, DataCtor> ref() {
      return ref;
    }
  }

  /**
   * Concrete data definition
   *
   * @author kiva
   * @see DataDef
   */
  public static final class DataDecl extends Decl {
    public final @NotNull DefVar<DataDef, DataDecl> ref;
    public @NotNull Expr result;
    public @NotNull Either<Ctors, Clauses> body;

    public DataDecl(
      @NotNull SourcePos sourcePos,
      @NotNull Accessibility accessibility,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      @NotNull Either<Ctors, Clauses> body,
      @NotNull ImmutableSeq<Stmt> abuseBlock
    ) {
      super(sourcePos, accessibility, abuseBlock, telescope);
      this.result = result;
      this.body = body;
      this.ref = DefVar.concrete(this, name);
      body.map(ctors -> {
        ctors.ctors.forEach(c -> c.dataRef = ref);
        return Unit.unit();
      }, clauses -> {
        clauses.clauses.forEach(t -> t._2.dataRef = ref);
        return Unit.unit();
      });
    }

    @Override protected <P, R> R doAccept(@NotNull Decl.Visitor<P, R> visitor, P p) {
      return visitor.visitData(this, p);
    }

    @Override public @NotNull DefVar<DataDef, DataDecl> ref() {
      return this.ref;
    }

    public static record Clauses(@NotNull ImmutableSeq<Tuple2<Pattern, DataCtor>> clauses) {
    }

    public static record Ctors(@NotNull ImmutableSeq<DataCtor> ctors) {
    }
  }

  /**
   * Concrete structure definition
   *
   * @author vont
   */
  public static final class StructDecl extends Decl {
    public final @NotNull DefVar<? extends Def, StructDecl> ref;

    public StructDecl(
      @NotNull SourcePos sourcePos,
      @NotNull Accessibility accessibility,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> fieldTelescope,
      // @NotNull ImmutableSeq<String> superClassNames,
      @NotNull ImmutableSeq<StructField> fields,
      @NotNull ImmutableSeq<Stmt> abuseBlock) {
      super(sourcePos, accessibility, abuseBlock, fieldTelescope);
      this.ref = DefVar.concrete(this, name);
    }

    @Override public @NotNull DefVar<? extends Def, StructDecl> ref() {
      return ref;
    }

    @Override protected <P, R> R doAccept(Decl.@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitStruct(this, p);
    }
  }

  public record StructField() {
  }

  /**
   * Concrete function definition
   *
   * @author re-xyr
   * @see FnDef
   */
  public static final class FnDecl extends Decl {
    public final @NotNull EnumSet<Modifier> modifiers;
    public final @Nullable Assoc assoc;
    public final @NotNull DefVar<FnDef, FnDecl> ref;
    public @NotNull Expr result;
    public @NotNull Either<Expr, ImmutableSeq<Pattern.Clause>> body;

    public FnDecl(
      @NotNull SourcePos sourcePos,
      @NotNull Accessibility accessibility,
      @NotNull EnumSet<Modifier> modifiers,
      @Nullable Assoc assoc,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      @NotNull Either<Expr, ImmutableSeq<Pattern.Clause>> body,
      @NotNull ImmutableSeq<Stmt> abuseBlock
    ) {
      super(sourcePos, accessibility, abuseBlock, telescope);
      this.modifiers = modifiers;
      this.assoc = assoc;
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
  }
}

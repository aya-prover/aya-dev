// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Either;
import kala.control.Option;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.generic.Modifier;
import org.aya.ref.DefVar;
import org.aya.resolve.context.Context;
import org.aya.tyck.pat.PatTycker;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Concrete definition, corresponding to {@link Def}.
 *
 * @author re-xyr
 */
public sealed abstract class TopTeleDecl extends BaseDecl.Telescopic implements TopLevelDecl {
  public final @NotNull Accessibility accessibility;
  public @Nullable Context ctx = null;
  public @NotNull Expr result;
  public final @NotNull TopLevelDecl.Personality personality;

  @Override public @NotNull TopLevelDecl.Personality personality() {
    return personality;
  }

  @Override public @NotNull Accessibility accessibility() {
    return accessibility;
  }

  @Override public @Nullable Context getCtx() {
    return ctx;
  }

  @Override public void setCtx(@NotNull Context ctx) {
    this.ctx = ctx;
  }

  @Override public @NotNull Expr result() {
    return result;
  }

  @Override public void setResult(@NotNull Expr result) {
    this.result = result;
  }

  protected TopTeleDecl(
    @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
    @NotNull Accessibility accessibility,
    @Nullable OpInfo opInfo,
    @NotNull BindBlock bindBlock,
    @NotNull ImmutableSeq<Expr.Param> telescope,
    @NotNull Expr result,
    @NotNull TopTeleDecl.Personality personality
  ) {
    super(sourcePos, entireSourcePos, opInfo, bindBlock, telescope);
    this.accessibility = accessibility;
    this.result = result;
    this.personality = personality;
  }

  @Contract(pure = true) public abstract @NotNull DefVar<? extends Def, ? extends TopTeleDecl> ref();

  /**
   * @author ice1000
   * @implSpec the result field of {@link PrimDecl} might be {@link org.aya.concrete.Expr.ErrorExpr},
   * which means it's unspecified in the concrete syntax.
   * @see PrimDef
   */
  public static final class PrimDecl extends TopTeleDecl {
    public final @NotNull DefVar<PrimDef, PrimDecl> ref;

    public PrimDecl(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result
    ) {
      super(sourcePos, entireSourcePos, Accessibility.Public, null, BindBlock.EMPTY, telescope, result, Personality.NORMAL);
      this.ref = DefVar.concrete(this, name);
    }

    @Override public boolean needTyck(@NotNull ImmutableSeq<String> currentMod) {
      return ref.isInModule(currentMod) && signature == null;
    }

    @Override public @NotNull DefVar<PrimDef, PrimDecl> ref() {
      return ref;
    }
  }

  public static final class DataCtor extends BaseDecl.Telescopic {
    public final @NotNull DefVar<CtorDef, TopTeleDecl.DataCtor> ref;
    public DefVar<DataDef, DataDecl> dataRef;
    /** Similar to {@link BaseDecl.Telescopic#signature}, but stores the bindings in {@link DataCtor#patterns} */
    public ImmutableSeq<Term.Param> patternTele;
    public @NotNull ImmutableSeq<Pattern.Clause> clauses;
    public @NotNull ImmutableSeq<Pattern> patterns;
    public final boolean coerce;

    /** used when tycking constructor's header */
    public @Nullable ImmutableSeq<Pat> yetTyckedPat;
    /** used when tycking constructor's header */
    public @Nullable PatTycker yetTycker;

    public DataCtor(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @Nullable OpInfo opInfo,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull ImmutableSeq<Pattern.Clause> clauses,
      @NotNull ImmutableSeq<Pattern> patterns,
      boolean coerce,
      @NotNull BindBlock bindBlock
    ) {
      super(sourcePos, entireSourcePos, opInfo, bindBlock, telescope);
      this.clauses = clauses;
      this.coerce = coerce;
      this.patterns = patterns;
      this.ref = DefVar.concrete(this, name);
    }

    @Override public @NotNull DefVar<CtorDef, DataCtor> ref() {
      return ref;
    }
  }

  /**
   * Concrete data definition
   *
   * @author kiva
   * @see DataDef
   */
  public static final class DataDecl extends TopTeleDecl {
    public final @NotNull DefVar<DataDef, DataDecl> ref;
    public final @NotNull ImmutableSeq<DataCtor> body;
    /** Yet type-checked constructors */
    public final @NotNull MutableList<@NotNull CtorDef> checkedBody = MutableList.create();
    public int ulift;

    public DataDecl(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @NotNull Accessibility accessibility,
      @Nullable OpInfo opInfo,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      @NotNull ImmutableSeq<DataCtor> body,
      @NotNull BindBlock bindBlock,
      @NotNull TopTeleDecl.Personality personality
    ) {
      super(sourcePos, entireSourcePos, accessibility, opInfo, bindBlock, telescope, result, personality);
      this.body = body;
      this.ref = DefVar.concrete(this, name);
      body.forEach(ctors -> ctors.dataRef = ref);
    }

    @Override public @NotNull DefVar<DataDef, DataDecl> ref() {
      return this.ref;
    }
  }

  public static final class StructField extends BaseDecl.Telescopic {
    public final @NotNull DefVar<FieldDef, TopTeleDecl.StructField> ref;
    public DefVar<StructDef, StructDecl> structRef;
    public @NotNull ImmutableSeq<Pattern.Clause> clauses;
    public @NotNull Expr result;
    public @NotNull Option<Expr> body;

    public final boolean coerce;

    public StructField(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @Nullable OpInfo opInfo,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      @NotNull Option<Expr> body,
      @NotNull ImmutableSeq<Pattern.Clause> clauses,
      boolean coerce,
      @NotNull BindBlock bindBlock
    ) {
      super(sourcePos, entireSourcePos, opInfo, bindBlock, telescope);
      this.coerce = coerce;
      this.result = result;
      this.clauses = clauses;
      this.body = body;
      this.ref = DefVar.concrete(this, name);
    }

    @Override public @NotNull DefVar<FieldDef, StructField> ref() {
      return ref;
    }
  }

  /**
   * Concrete function definition
   *
   * @author re-xyr
   * @see FnDef
   */
  public static final class FnDecl extends TopTeleDecl {
    public final @NotNull EnumSet<Modifier> modifiers;
    public final @NotNull DefVar<FnDef, FnDecl> ref;
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
      @NotNull BindBlock bindBlock,
      @NotNull TopTeleDecl.Personality personality
    ) {
      super(sourcePos, entireSourcePos, accessibility, opInfo, bindBlock, telescope, result, personality);
      this.modifiers = modifiers;
      this.ref = DefVar.concrete(this, name);
      this.body = body;
    }

    @Override public @NotNull DefVar<FnDef, FnDecl> ref() {
      return this.ref;
    }
  }
}

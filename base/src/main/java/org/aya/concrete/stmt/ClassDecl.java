// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.core.def.ClassDef;
import org.aya.core.def.Def;
import org.aya.core.def.FieldDef;
import org.aya.core.def.StructDef;
import org.aya.ref.DefVar;
import org.aya.resolve.context.Context;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Concrete classable definitions, corresponding to {@link ClassDef}.
 *
 * @author zaoqi
 * @see Decl
 */
public sealed abstract class ClassDecl extends CommonDecl implements Decl.Resulted, Decl.TopLevel {
  private final @NotNull Decl.Personality personality;
  public @Nullable Context ctx = null;
  public @NotNull Expr result;

  @Override public @NotNull Decl.Personality personality() {
    return personality;
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

  protected ClassDecl(
    @NotNull SourcePos sourcePos,
    @NotNull SourcePos entireSourcePos,
    @Nullable OpDecl.OpInfo opInfo,
    @NotNull BindBlock bindBlock,
    @NotNull Expr result,
    @NotNull Decl.Personality personality,
    @NotNull Accessibility accessibility
  ) {
    super(sourcePos, entireSourcePos, accessibility, opInfo, bindBlock);
    this.result = result;
    this.personality = personality;
  }

  @Override public String toString() {
    return getClass().getSimpleName() + "[" + ref().name() + "]";
  }

  /**
   * Concrete structure definition
   *
   * @author vont
   */
  public static final class StructDecl extends ClassDecl {
    public final @NotNull DefVar<StructDef, StructDecl> ref;
    public @NotNull
    final ImmutableSeq<StructField> fields;
    public int ulift;

    public StructDecl(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @NotNull Accessibility accessibility,
      @Nullable OpInfo opInfo,
      @NotNull String name,
      @NotNull Expr result,
      // @NotNull ImmutableSeq<String> superClassNames,
      @NotNull ImmutableSeq<StructField> fields,
      @NotNull BindBlock bindBlock,
      @NotNull Decl.Personality personality
    ) {
      super(sourcePos, entireSourcePos, opInfo, bindBlock, result, personality, accessibility);
      this.fields = fields;
      this.ref = DefVar.concrete(this, name);
      fields.forEach(field -> field.structRef = ref);
    }

    @Override public @NotNull DefVar<StructDef, StructDecl> ref() {
      return ref;
    }

      public static final class StructField extends CommonDecl implements Decl.Telescopic, Decl.Resulted {
        public final @NotNull DefVar<FieldDef, StructField> ref;
        public DefVar<StructDef, StructDecl> structRef;
        public @NotNull ImmutableSeq<Pattern.Clause> clauses;
        public @NotNull Expr result;
        public @NotNull Option<Expr> body;
        public final boolean coerce;

        // will change after resolve
        public @NotNull ImmutableSeq<Expr.Param> telescope;
        public @Nullable Def.Signature signature;

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
          super(sourcePos, entireSourcePos, Accessibility.Public, opInfo, bindBlock);
          this.coerce = coerce;
          this.result = result;
          this.clauses = clauses;
          this.body = body;
          this.ref = DefVar.concrete(this, name);
          this.telescope = telescope;
        }

        @Override public @NotNull DefVar<FieldDef, StructField> ref() {
          return ref;
        }

        @Override public @NotNull ImmutableSeq<Expr.Param> telescope() {
          return telescope;
        }

        @Override public void setTelescope(@NotNull ImmutableSeq<Expr.Param> telescope) {
          this.telescope = telescope;
        }

        @Override public @Nullable Def.Signature signature() {
          return signature;
        }

        @Override public void setSignature(Def.@Nullable Signature signature) {
          this.signature = signature;
        }

        @Override public @NotNull Expr result() {
          return result;
        }

        @Override public void setResult(@NotNull Expr result) {
          this.result = result;
        }
      }
  }
}

// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.Map;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.core.def.ClassDef;
import org.aya.core.def.Def;
import org.aya.core.def.FieldDef;
import org.aya.core.def.StructDef;
import org.aya.core.term.StructCall;
import org.aya.ref.DefVar;
import org.aya.resolve.context.Context;
import org.aya.util.MutableGraph;
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
    // `StructCall`s
    // This will be desugared so StructDef doesn't need to store this.
    public final @NotNull ImmutableSeq<Expr> parents;
    // set in tyck
    // rootRef -> StructField
    public @Nullable ImmutableMap<DefVar<FieldDef, StructField>, StructField> fieldMap = null;
    public int ulift;

    public @NotNull ImmutableMap<DefVar<FieldDef, StructField>, StructField> calculateFieldMap(
      @NotNull ImmutableSeq<StructCall> parents
    ) {
      if (fieldMap == null) fieldMap = collectFields(parents).toImmutableMap();
      return fieldMap;
    }

    private @NotNull MutableMap<DefVar<FieldDef, StructField>, StructField> collectFields(@NotNull ImmutableSeq<StructCall> parents) {
      var fieldMap = MutableMap.create();
      for (var parent : parents) {
        // TODO: diamond inheritance
        var implicitOverrides = parent.params();
        var implicitFields = implicitOverrides.map(t -> new StructField(t._1.concrete, (Expr) (Object) t._2.term())); // TODO: implement this
        parent.ref().concrete.fieldMap.forEach((field, structField) -> {
          var x = fieldMap.put(field.concrete.rootRef, structField);
          if (x.isDefined()) {
            throw new IllegalStateException("Duplicate field: " + field); // TODO: better error
          }
        });
        implicitFields.forEach(field -> {
          var x = fieldMap.put(field.rootRef, field);
          if (x.isDefined()) {
            throw new IllegalStateException("Duplicate field: " + field); // TODO: better error
          }
        });
      }
    }

    public StructDecl(
      @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
      @NotNull Accessibility accessibility,
      @Nullable OpInfo opInfo,
      @NotNull String name,
      @NotNull Expr result,
      @NotNull ImmutableSeq<Expr> parents,
      @NotNull ImmutableSeq<StructField> fields,
      @NotNull BindBlock bindBlock,
      @NotNull Decl.Personality personality
    ) {
      super(sourcePos, entireSourcePos, opInfo, bindBlock, result, personality, accessibility);
      this.parents = parents;
      this.fields = fields;
      this.ref = DefVar.concrete(this, name);
      fields.forEach(field -> field.structRef = ref);
    }

    @Override public @NotNull DefVar<StructDef, StructDecl> ref() {
      return ref;
    }

    public static final class StructField extends CommonDecl implements Decl.Telescopic, Decl.Resulted {
      public final @NotNull DefVar<FieldDef, StructField> rootRef;
      public final @NotNull Option<DefVar<FieldDef, StructField>> parentRef;
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
        this.rootRef = this.ref;
        this.parentRef = Option.none();
        this.telescope = telescope;
      }

      public StructField(@NotNull StructField parent, @NotNull Expr result) {
        super(parent.sourcePos, parent.entireSourcePos, Accessibility.Public, parent.opInfo, parent.bindBlock);
        if (parent.telescope.isNotEmpty() || parent.body.isDefined() || parent.clauses.isNotEmpty() || parent.coerce)
          throw new UnsupportedOperationException("TODO");
        this.coerce = parent.coerce;
        this.result = result;
        this.clauses = parent.clauses;
        this.body = parent.body;
        this.ref = DefVar.concrete(this, parent.ref.name());
        this.rootRef = parent.rootRef;
        this.parentRef = Option.some(parent.ref);
        this.telescope = parent.telescope;
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

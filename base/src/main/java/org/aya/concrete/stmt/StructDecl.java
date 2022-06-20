// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.aya.core.def.StructDef;
import org.aya.ref.DefVar;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Concrete structure definition
 *
 * @author vont
 */
public final class StructDecl extends ClassDecl {
  public final @NotNull DefVar<StructDef, StructDecl> ref;
  public final @NotNull ImmutableSeq<Expr> parents;
  public @NotNull
  final ImmutableSeq<TopTeleDecl.StructField> fields;
  public int ulift;

  public StructDecl(
    @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
    @NotNull Stmt.Accessibility accessibility,
    @Nullable OpDecl.OpInfo opInfo,
    @NotNull String name,
    @NotNull Expr result,
    // @NotNull ImmutableSeq<String> superClassNames,
    @NotNull ImmutableSeq<Expr> parents,
    @NotNull ImmutableSeq<TopTeleDecl.StructField> fields,
    @NotNull BindBlock bindBlock,
    @NotNull Personality personality) {
    super(sourcePos, entireSourcePos, opInfo, bindBlock,result, personality, accessibility);
    this.fields = fields;
    this.ref = DefVar.concrete(this, name);
    this.parents = parents;
    fields.forEach(field -> field.structRef = ref);
  }

  @Override public @NotNull DefVar<StructDef, org.aya.concrete.stmt.StructDecl> ref() {
    return ref;
  }
}

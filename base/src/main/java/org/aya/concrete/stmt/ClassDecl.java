// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import org.aya.concrete.Expr;
import org.aya.core.def.ClassDef;
import org.aya.ref.DefVar;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An item in the signature, with fields and result type.
 * Concrete definition, corresponding to {@link ClassDef}.
 *
 * @author zaoqi
 */
public non-sealed/*sealed*/ abstract class ClassDecl implements Stmt, TopLevelDecl {
  public final @NotNull SourcePos sourcePos;
  public final @NotNull SourcePos entireSourcePos;
  public final @Nullable OpDecl.OpInfo opInfo;
  public final @NotNull BindBlock bindBlock;
  public @NotNull Expr result;
  public final @NotNull TopLevelDecl.Personality personality;

  public final @NotNull Accessibility accessibility;

  @Override public @NotNull TopLevelDecl.Personality personality() {
    return personality;
  }

  @Override public @NotNull Accessibility accessibility() {
    return accessibility;
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
    this.sourcePos = sourcePos;
    this.entireSourcePos = entireSourcePos;
    this.opInfo = opInfo;
    this.bindBlock = bindBlock;
    this.result = result;
    this.personality = personality;
    this.accessibility = accessibility;
  }

  @Override @NotNull public abstract DefVar<? extends ClassDef, ? extends ClassDecl> ref();

  @Override public @NotNull SourcePos sourcePos() {
    return sourcePos;
  }

  @Override public String toString() {
    return getClass().getSimpleName() + "[" + ref().name() + "]";
  }

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
    return doAccept((Visitor<P, R>) visitor, p);
  }

  public interface Visitor<P, R> extends TopLevelDecl.Visitor<P, R> {
  }
}

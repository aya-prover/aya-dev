// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.aya.core.def.ClassDef;
import org.aya.ref.ClassDefVar;
import org.aya.tyck.order.TyckUnit;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An item in the signature, with fields and result type.
 * Concrete definition, corresponding to {@link ClassDef}.
 *
 * @author zaoqi
 */
public non-sealed/*sealed*/ abstract class ClassDecl implements SourceNode, TyckUnit, Stmt, GenericTopLevelDecl {
  public final @NotNull SourcePos sourcePos;
  public final @NotNull SourcePos entireSourcePos;
  public final @Nullable OpDecl.OpInfo opInfo;
  public final @NotNull BindBlock bindBlock;
  public @NotNull Expr result;
  public final @NotNull GenericTopLevelDecl.Personality personality;

  public final @NotNull Accessibility accessibility;

  @Override public @NotNull GenericTopLevelDecl.Personality personality() {
    return personality;
  }

  @Override public @NotNull Accessibility accessibility() {
    return accessibility;
  }

  protected ClassDecl(@NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos, OpDecl.OpInfo opInfo, @NotNull BindBlock bindBlock, @NotNull Expr result, Decl.Personality personality, @NotNull Accessibility accessibility) {
    this.sourcePos = sourcePos;
    this.entireSourcePos = entireSourcePos;
    this.opInfo = opInfo;
    this.bindBlock = bindBlock;
    this.result = result;
    this.personality = personality;
    this.accessibility = accessibility;
  }

  @Contract(pure = true)
  abstract public @NotNull ClassDefVar<?, ?> ref();

  @Override public @NotNull SourcePos sourcePos() {
    return sourcePos;
  }

  @Override public boolean needTyck(@NotNull ImmutableSeq<String> currentMod) {
    return ref().isInModule(currentMod) && ref().core == null;
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

  public interface Visitor<P, R> extends GenericTopLevelDecl.Visitor<P, R> {
  }
}

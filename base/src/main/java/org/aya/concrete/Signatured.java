// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.concrete.ConcreteDecl;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.core.def.Def;
import org.aya.tyck.StmtTycker;
import org.aya.tyck.trace.Trace;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An item in the signature, with telescope and result type.
 *
 * @author ice1000
 */
public sealed abstract class Signatured implements ConcreteDecl permits Decl, Decl.DataCtor, Decl.StructField {
  public final @NotNull SourcePos sourcePos;

  // will change after resolve
  public @NotNull ImmutableSeq<Expr.Param> telescope;
  public Def.@Nullable Signature signature;

  @Override public @NotNull SourcePos sourcePos() {
    return sourcePos;
  }

  public Def tyck(@NotNull Reporter reporter, Trace.@Nullable Builder builder) {
    var tycker = new StmtTycker(reporter, builder);
    return accept(tycker, tycker.newTycker());
  }

  public interface Visitor<P, R> extends Decl.Visitor<P, R> {
    default void traceEntrance(@NotNull Signatured item, P p) {
    }
    @ApiStatus.NonExtendable
    @Override default void traceEntrance(@NotNull Decl decl, P p) {
      traceEntrance((Signatured) decl, p);
    }
    R visitCtor(@NotNull Decl.DataCtor ctor, P p);
    R visitField(@NotNull Decl.StructField field, P p);
  }

  protected abstract <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  public final <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    visitor.traceEntrance(this, p);
    var ret = doAccept(visitor, p);
    visitor.traceExit(p, ret);
    return ret;
  }

  protected Signatured(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Expr.Param> telescope
  ) {
    this.sourcePos = sourcePos;
    this.telescope = telescope;
  }
}

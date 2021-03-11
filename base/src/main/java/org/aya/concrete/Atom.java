// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.ref.LocalVar;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva, ice1000
 */
public sealed interface Atom {
  interface Visitor<P, R> {
    R visitTuple(@NotNull Tuple tuple, P p);
    R visitBraced(@NotNull Braced braced, P p);
    R visitNumber(@NotNull Number number, P p);
    R visitBind(@NotNull Bind bind, P p);
    R visitCalmFace(@NotNull CalmFace calmFace, P p);
  }

  @NotNull SourcePos sourcePos();

  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

  record Tuple(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<Pattern> patterns) implements Atom {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTuple(this, p);
    }
  }

  record Braced(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<Pattern> patterns) implements Atom {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitBraced(this, p);
    }
  }

  record Number(@NotNull SourcePos sourcePos, int number) implements Atom {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitNumber(this, p);
    }
  }

  record CalmFace(@NotNull SourcePos sourcePos) implements Atom {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCalmFace(this, p);
    }
  }

  /**
   * @param resolved will be modified during resolving
   */
  record Bind(
    @NotNull SourcePos sourcePos,
    @NotNull LocalVar bind,
    @NotNull Ref<Var> resolved
  ) implements Atom {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitBind(this, p);
    }
  }
}

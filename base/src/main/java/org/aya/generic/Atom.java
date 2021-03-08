// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.generic;

import org.aya.api.error.SourcePos;
import org.aya.ref.LocalVar;
import org.glavo.kala.annotations.Covariant;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva, ice1000
 */
public sealed interface Atom<@Covariant Pat> {
  interface Visitor<Pat, P, R> {
    R visitTuple(@NotNull Tuple<Pat> tuple, P p);
    R visitBraced(@NotNull Braced<Pat> tuple, P p);
    R visitNumber(@NotNull Number<Pat> tuple, P p);
    R visitBind(@NotNull Bind<Pat> tuple, P p);
    R visitCalmFace(P p);
  }

  @NotNull SourcePos sourcePos();

  <P, R> R accept(@NotNull Visitor<Pat, P, R> visitor, P p);

  record Tuple<Pat>(@NotNull SourcePos sourcePos, @NotNull Buffer<Pat> patterns) implements Atom<Pat> {
    @Override public <P, R> R accept(@NotNull Visitor<Pat, P, R> visitor, P p) {
      return visitor.visitTuple(this, p);
    }
  }

  record Braced<Pat>(@NotNull SourcePos sourcePos, @NotNull Buffer<Pat> patterns) implements Atom<Pat> {
    @Override public <P, R> R accept(@NotNull Visitor<Pat, P, R> visitor, P p) {
      return visitor.visitBraced(this, p);
    }
  }

  record Number<Pat>(@NotNull SourcePos sourcePos, int number) implements Atom<Pat> {
    @Override public <P, R> R accept(@NotNull Visitor<Pat, P, R> visitor, P p) {
      return visitor.visitNumber(this, p);
    }
  }

  record CalmFace<Pat>(@NotNull SourcePos sourcePos) implements Atom<Pat> {
    @Override public <P, R> R accept(@NotNull Visitor<Pat, P, R> visitor, P p) {
      return visitor.visitCalmFace(p);
    }
  }

  record Bind<Pat>(@NotNull SourcePos sourcePos, @NotNull LocalVar bind) implements Atom<Pat> {
    @Override public <P, R> R accept(@NotNull Visitor<Pat, P, R> visitor, P p) {
      return visitor.visitBind(this, p);
    }
  }
}

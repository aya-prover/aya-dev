// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.error.SourcePos;
import org.aya.generic.Atom;
import org.aya.ref.LocalVar;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva, ice1000
 */
public sealed interface Pattern {
  @NotNull SourcePos sourcePos();
  @Nullable LocalVar as();
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

  interface Visitor<P, R> {
    R visitAtomic(@NotNull Atomic atomic, P p);
    R visitCtor(@NotNull Ctor ctor, P p);
  }

  record Atomic(
    @NotNull SourcePos sourcePos,
    @NotNull Atom<Pattern> atom,
    @Nullable LocalVar as
  ) implements Pattern {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitAtomic(this, p);
    }
  }

  record Ctor(
    @NotNull SourcePos sourcePos,
    @NotNull String name,
    @NotNull Seq<Pattern> params,
    @Nullable LocalVar as
  ) implements Pattern {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCtor(this, p);
    }
  }

  /**
   * @author kiva
   */
  sealed interface Clause {
    <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

    interface Visitor<P, R> {
      R visitMatch(@NotNull Match match, P p);
      R visitAbsurd(@NotNull Absurd absurd, P p);
    }

    record Match(
      @NotNull Seq<Pattern> patterns,
      @NotNull Expr expr
    ) implements Clause {
      @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
        return visitor.visitMatch(this, p);
      }
    }

    record Absurd() implements Clause {
      @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
        return visitor.visitAbsurd(this, p);
      }
    }
  }
}

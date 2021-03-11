// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pat;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.def.DataDef;
import org.aya.core.term.Term;
import org.aya.ref.LocalVar;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva, ice1000
 */
public sealed interface Pat {
  @Nullable LocalVar as();
  @NotNull Term type();
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);
  default @NotNull Term toTerm() {
    return accept(PatToTerm.INSTANCE, Unit.unit());
  }

  interface Visitor<P, R> {
    R visitBind(@NotNull Bind bind, P p);
    R visitTuple(@NotNull Tuple tuple, P p);
    R visitCtor(@NotNull Ctor ctor, P p);
  }

  record Bind(
    @NotNull LocalVar as,
    @NotNull Term type
  ) implements Pat {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitBind(this, p);
    }
  }

  record Tuple(
    @NotNull ImmutableSeq<Pat> pats,
    @Nullable LocalVar as,
    @NotNull Term type
  ) implements Pat {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTuple(this, p);
    }
  }

  record Ctor(
    @NotNull DefVar<DataDef.Ctor, Decl.DataCtor> ref,
    @NotNull ImmutableSeq<Pat> params,
    @Nullable LocalVar as,
    @NotNull Term type
  ) implements Pat {
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
      R visitAbsurd(P p);
    }

    record Match(
      @NotNull Seq<Pat> patterns,
      @NotNull Term expr
    ) implements Clause {
      @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
        return visitor.visitMatch(this, p);
      }
    }

    final class Absurd implements Clause {
      public static final @NotNull Absurd INSTANCE = new Absurd();

      private Absurd() {
      }

      @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
        return visitor.visitAbsurd(p);
      }
    }
  }
}

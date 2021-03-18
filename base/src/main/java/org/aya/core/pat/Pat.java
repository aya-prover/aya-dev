// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pat;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.def.DataDef;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.ref.LocalVar;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Option;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva, ice1000
 */
@Debug.Renderer(text = "toTerm().toDoc().renderWithPageWidth(114514)")
public sealed interface Pat {
  @Nullable LocalVar as();
  @NotNull Term type();
  boolean explicit();
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
    boolean explicit,
    @NotNull LocalVar as,
    @NotNull Term type
  ) implements Pat {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitBind(this, p);
    }
  }

  record Tuple(
    boolean explicit,
    @NotNull ImmutableSeq<Pat> pats,
    @Nullable LocalVar as,
    @NotNull Term type
  ) implements Pat {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTuple(this, p);
    }
  }

  record Ctor(
    boolean explicit,
    @NotNull DefVar<DataDef.Ctor, Decl.DataCtor> ref,
    @NotNull ImmutableSeq<Pat> params,
    @Nullable LocalVar as,
    @NotNull CallTerm.Data type
  ) implements Pat {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCtor(this, p);
    }
  }

  /**
   * @author kiva, ice1000
   */
  record Clause(
    @NotNull ImmutableSeq<Pat> patterns,
    @NotNull Term expr
  ) {
    public static @NotNull Option<@NotNull Clause> fromProto(@NotNull PrototypeClause clause) {
      return clause.expr.map(term -> new Clause(clause.patterns, term));
    }
  }

  /**
   * @author ice1000
   */
  record PrototypeClause(
    @NotNull ImmutableSeq<Pat> patterns,
    @NotNull Option<Term> expr
  ) {
    public static @NotNull PrototypeClause prototypify(@NotNull Clause clause) {
      return new PrototypeClause(clause.patterns, Option.some(clause.expr));
    }
  }
}

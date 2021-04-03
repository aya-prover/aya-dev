// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pat;

import org.aya.api.core.CorePat;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.concrete.Decl;
import org.aya.core.def.DataDef;
import org.aya.core.def.PrimDef;
import org.aya.core.pretty.PatPrettier;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.generic.Matching;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.LocalCtx;
import org.glavo.kala.collection.SeqLike;
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
public sealed interface Pat extends CorePat {
  @Override @NotNull Term type();
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);
  @Override default @NotNull Term toTerm() {
    return accept(PatToTerm.INSTANCE, Unit.unit());
  }
  @Override default @NotNull Arg<Term> toArg() {
    return new Arg<>(toTerm(), explicit());
  }
  @Override default @NotNull Doc toDoc() {
    return accept(PatPrettier.INSTANCE, false);
  }
  default void storeBindings(@NotNull LocalCtx localCtx) {
    accept(new PatTyper(localCtx), Unit.unit());
  }
  static @NotNull ImmutableSeq<Term.Param> extractTele(@NotNull SeqLike<Pat> pats) {
    var localCtx = new LocalCtx();
    for (var pat : pats) pat.accept(new PatTyper(localCtx), Unit.unit());
    return localCtx.extract();
  }

  interface Visitor<P, R> {
    R visitBind(@NotNull Bind bind, P p);
    R visitTuple(@NotNull Tuple tuple, P p);
    R visitCtor(@NotNull Ctor ctor, P p);
    R visitAbsurd(@NotNull Absurd absurd, P p);
    R visitPrim(@NotNull Pat.Prim prim, P p);
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

  record Absurd(
    boolean explicit,
    @NotNull Term type
  ) implements Pat {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitAbsurd(this, p);
    }

    @Override public @Nullable LocalVar as() {
      return null;
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

  record Prim(
    boolean explicit,
    @NotNull DefVar<PrimDef, Decl.PrimDecl> as,
    @NotNull Term type
  ) implements Pat {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitPrim(this, p);
    }
  }

  /**
   * @author ice1000
   */
  record PrototypeClause(
    @NotNull ImmutableSeq<Pat> patterns,
    @NotNull Option<Term> expr
  ) {
    public static @NotNull PrototypeClause prototypify(@NotNull Matching<Pat, Term> clause) {
      return new PrototypeClause(clause.patterns(), Option.some(clause.body()));
    }

    public static @NotNull Option<@NotNull Matching<Pat, Term>> deprototypify(@NotNull PrototypeClause clause) {
      return clause.expr.map(term -> new Matching<>(clause.patterns, term));
    }
  }
}

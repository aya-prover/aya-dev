// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pat;

import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.core.term.CallTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public class PatToTerm implements Pat.Visitor<Unit, Term> {
  static final @NotNull PatToTerm INSTANCE = new PatToTerm();

  protected PatToTerm() {
  }

  @Override public Term visitAbsurd(Pat.@NotNull Absurd absurd, Unit unit) {
    return new RefTerm(new LocalVar("()"), absurd.type());
  }

  @Override public Term visitPrim(Pat.@NotNull Prim prim, Unit unit) {
    return new CallTerm.Prim(prim.ref(), ImmutableSeq.of(), ImmutableSeq.empty());
  }

  @Override public Term visitBind(Pat.@NotNull Bind bind, Unit unit) {
    return new RefTerm(bind.as(), bind.type());
  }

  @Override public Term visitTuple(Pat.@NotNull Tuple tuple, Unit unit) {
    return new IntroTerm.Tuple(tuple.pats().map(p -> p.accept(this, Unit.unit())));
  }

  @Override public Term visitCtor(Pat.@NotNull Ctor ctor, Unit unit) {
    var data = (CallTerm.Data) ctor.type();
    var core = ctor.ref().core;
    var tele = core.conTele();
    var args = ctor.params().view().zip(tele.view())
      .map(p -> new Arg<>(p._1.accept(this, Unit.unit()), p._2.explicit()))
      .collect(ImmutableSeq.factory());
    var dataArgs = core.dataTele().map(Term.Param::toArg);
    return new CallTerm.Con(data.ref(), ctor.ref(), dataArgs, data.sortArgs(), args);
  }
}

// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pat;

import org.aya.core.term.CallTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.term.TupTerm;
import org.aya.generic.Arg;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
final class PatToTerm implements Pat.Visitor<Unit, Term> {
  static final @NotNull PatToTerm INSTANCE = new PatToTerm();

  private PatToTerm() {
  }

  @Override public Term visitAbsurd(Pat.@NotNull Absurd absurd, Unit unit) {
    throw new IllegalStateException();
  }

  @Override public Term visitBind(Pat.@NotNull Bind bind, Unit unit) {
    return new RefTerm(bind.as());
  }

  @Override public Term visitTuple(Pat.@NotNull Tuple tuple, Unit unit) {
    return new TupTerm(tuple.pats().map(p -> p.accept(this, Unit.unit())));
  }

  @Override public Term visitCtor(Pat.@NotNull Ctor ctor, Unit unit) {
    var data = (CallTerm.Data) ctor.type();
    var tele = ctor.ref().core.conTelescope();
    var args = ctor.params().view().zip(tele.view())
      .map(p -> new Arg<>(p._1.accept(this, Unit.unit()), p._2.explicit()))
      .collect(Seq.factory());
    return new CallTerm.Con(ctor.ref(), data.contextArgs(), data.args(), args);
  }
}

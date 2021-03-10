// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pat;

import org.aya.core.term.AppTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.generic.Arg;
import org.aya.generic.Atom;
import org.aya.ref.LocalVar;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public final class PatToTerm implements Pat.Visitor<Unit, Term>, Atom.Visitor<Pat, Unit, Term> {
  public static final @NotNull PatToTerm INSTANCE = new PatToTerm();

  private PatToTerm() {
  }

  @Override public Term visitAtomic(Pat.@NotNull Atomic atomic, Unit unit) {
    return atomic.atom().accept(this, unit);
  }

  @Override public Term visitBraced(Atom.@NotNull Braced<Pat> braced, Unit unit) {
    throw new UnsupportedOperationException();
  }

  @Override public Term visitNumber(Atom.@NotNull Number<Pat> number, Unit unit) {
    throw new UnsupportedOperationException();
  }

  @Override public Term visitBind(Atom.@NotNull Bind<Pat> bind, Unit unit) {
    return new RefTerm(bind.bind());
  }

  @Override public Term visitCalmFace(Atom.@NotNull CalmFace<Pat> calmFace, Unit unit) {
    return new RefTerm(new LocalVar("_"));
  }

  @Override public Term visitTuple(Atom.@NotNull Tuple<Pat> tuple, Unit unit) {
    throw new UnsupportedOperationException();
  }

  @Override public Term visitCtor(Pat.@NotNull Ctor ctor, Unit unit) {
    var data = (AppTerm.DataCall) ctor.type();
    var tele = ctor.name().core.conTelescope();
    var args = ctor.params().view().zip(tele.view())
      .map(p -> new Arg<>(p._1.accept(this, Unit.unit()), p._2.explicit()))
      .collect(Seq.factory());
    return new AppTerm.ConCall(ctor.name(), data.args(), args);
  }
}

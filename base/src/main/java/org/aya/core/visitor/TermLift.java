// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.CallTerm;
import org.aya.core.term.ElimTerm;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

public record TermLift(TermView view, int ulift) implements TermView {
  @Override public @NotNull Term initial() {
    return view.initial();
  }

  @Override public TermView lift(int shift) {
    return new TermLift(view, ulift + shift);
  }

  @Override public Term post(Term term) {
    // TODO: Implement the correct rules.
    return switch (view.post(term)) {
      case FormTerm.Univ univ -> new FormTerm.Univ(univ.lift() + ulift);
      case ElimTerm.Proj proj -> new ElimTerm.Proj(proj.of(), proj.ulift() + ulift, proj.ix());
      case CallTerm.Struct struct -> new CallTerm.Struct(struct.ref(), struct.ulift() + ulift, struct.args());
      case CallTerm.Data data -> new CallTerm.Data(data.ref(), data.ulift() + ulift, data.args());
      case CallTerm.Con con -> {
        var head = con.head();
        head = new CallTerm.ConHead(head.dataRef(), head.ref(), head.ulift() + ulift, head.dataArgs());
        yield new CallTerm.Con(head, con.conArgs());
      }
      case CallTerm.Fn fn -> new CallTerm.Fn(fn.ref(), fn.ulift() + ulift, fn.args());
      case CallTerm.Access access -> new CallTerm.Access(access.of(), access.ref(), access.ulift() + ulift, access.structArgs(), access.fieldArgs());
      case CallTerm.Prim prim -> new CallTerm.Prim(prim.ref(), prim.ulift() + ulift, prim.args());
      case CallTerm.Hole hole -> new CallTerm.Hole(hole.ref(), hole.ulift() + ulift, hole.contextArgs(), hole.args());
      case Term misc -> misc;
    };
  }
}

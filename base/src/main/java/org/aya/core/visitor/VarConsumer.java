// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.api.ref.Var;
import org.aya.core.term.CallTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.generic.Arg;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface VarConsumer<P> extends TermConsumer<P> {
  @Override default Unit visitRef(@NotNull RefTerm term, P p) {
    visitVar(term.var(), p);
    return Unit.unit();
  }

  @Override default Unit visitHole(@NotNull CallTerm.Hole term, P p) {
    visitVar(term.var(), p);
    visitArgs(term.args(), p);
    return Unit.unit();
  }

  @Override default Unit visitFnCall(CallTerm.@NotNull Fn fnCall, P p) {
    visitVar(fnCall.fnRef(), p);
    visitArgs(fnCall.args(), p);
    return Unit.unit();
  }

  private void visitArgs(SeqLike<Arg<Term>> args, P p) {
    args.forEach(i -> i.term().accept(this, p));
  }

  @Override default Unit visitDataCall(@NotNull CallTerm.Data dataCall, P p) {
    visitVar(dataCall.dataRef(), p);
    visitArgs(dataCall.args(), p);
    return Unit.unit();
  }

  @Override default Unit visitConCall(@NotNull CallTerm.Con conCall, P p) {
    visitVar(conCall.conHead(), p);
    visitArgs(conCall.args(), p);
    return Unit.unit();
  }

  @Contract(mutates = "this,param2") void visitVar(Var usage, P p);
}

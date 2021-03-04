// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.api.ref.Var;
import org.aya.core.term.AppTerm;
import org.aya.core.term.RefTerm;
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

  @Override default Unit visitHole(@NotNull AppTerm.HoleApp term, P p) {
    visitVar(term.var(), p);
    return Unit.unit();
  }

  @Override default Unit visitFnCall(AppTerm.@NotNull FnCall fnCall, P p) {
    visitVar(fnCall.fnRef(), p);
    return Unit.unit();
  }

  @Override default Unit visitDataCall(@NotNull AppTerm.DataCall dataCall, P p) {
    visitVar(dataCall.dataRef(), p);
    return Unit.unit();
  }

  @Contract(mutates = "this,param2") void visitVar(Var usage, P p);
}

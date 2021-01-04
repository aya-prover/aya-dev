// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import org.glavo.kala.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.RefTerm;

/**
 * @author ice1000
 */
public interface VarConsumer<P> extends TermConsumer<P> {
  default Unit visitRef(@NotNull RefTerm term, P p) {
    visitVar(term.var(), p);
    return Unit.unit();
  }

  default Unit visitHole(@NotNull AppTerm.HoleApp term, P p) {
    visitVar(term.var(), p);
    return Unit.unit();
  }

  default Unit visitFnCall(AppTerm.@NotNull FnCall fnCall, P p) {
    visitVar(fnCall.fnRef(), p);
    return Unit.unit();
  }

  @Contract(mutates = "this,param2") void visitVar(Var usage, P p);
}

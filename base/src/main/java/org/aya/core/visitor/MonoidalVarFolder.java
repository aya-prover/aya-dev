// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.Tm;
import org.aya.ref.Var;

public interface MonoidalVarFolder<R> extends MonoidalFolder<R> {
  // TODO: Do we need to visit variables in `access` and `new` as well?
  R var(Var var);

  default R fold(Tm<R> tm) {
    return switch (tm) {
      case Tm.Struct<R> struct -> ops(struct.args().view().map(Tm.Arg::term).prepended(var(struct.ref())));
      case Tm.Data<R> data -> ops(data.args().view().map(Tm.Arg::term).prepended(var(data.ref())));
      case Tm.Con<R> con -> ops(con.head().dataArgs().view().map(Tm.Arg::term)
        .concat(con.args().view().map(Tm.Arg::term))
        .prepended(var(con.head().ref())));
      case Tm.Fn<R> fn -> ops(fn.args().view().map(Tm.Arg::term).prepended(var(fn.ref())));
      case Tm.Prim<R> prim -> ops(prim.args().view().map(Tm.Arg::term).prepended(var(prim.ref())));
      case Tm.Hole<R> hole -> ops(hole.args().view().map(Tm.Arg::term)
        .concat(hole.args().view().map(Tm.Arg::term))
        .prepended(var(hole.ref())));
      case Tm.Ref<R> ref -> var(ref.var());
      case Tm.Field<R> field -> var(field.ref());
      case Tm<R> t -> MonoidalFolder.super.fold(t);
    };
  }

  record Usages(Var var) implements MonoidalVarFolder<Integer> {
    @Override public Integer e() {
      return 0;
    }

    @Override public Integer op(Integer a, Integer b) {
      return a + b;
    }

    @Override public Integer var(Var v) {
      return v == var ? 1 : 0;
    }
  }
}

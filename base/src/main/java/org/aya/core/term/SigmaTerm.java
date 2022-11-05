// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import org.aya.core.visitor.BetaExpander;
import org.aya.core.visitor.Subst;
import org.aya.generic.SortKind;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public record SigmaTerm(@NotNull ImmutableSeq<@NotNull Param> params) implements StableWHNF, Term {
  public static @NotNull SortTerm max(@NotNull SortTerm x, @NotNull SortTerm y) {
    int lift = Math.max(x.lift(), y.lift());
    if (x.kind() == SortKind.Prop || y.kind() == SortKind.Prop) {
      return SortTerm.Prop;
    } else if (x.kind() == SortKind.Set || y.kind() == SortKind.Set) {
      return new SortTerm(SortKind.Set, lift);
    } else if (x.kind() == SortKind.Type || y.kind() == SortKind.Type) {
      return new SortTerm(SortKind.Type, lift);
    } else if (x.kind() == SortKind.ISet || y.kind() == SortKind.ISet) {
      // ice: this is controversial, but I think it's fine.
      // See https://github.com/agda/cubical/pull/910#issuecomment-1233113020
      return SortTerm.ISet;
    }
    throw new AssertionError("unreachable");
  }

  private static final Term I = IntervalTerm.INSTANCE;
  public @NotNull LamTerm coe(@NotNull CoeTerm coe, @NotNull LocalVar i) {
    var t = new RefTerm(new LocalVar("t"));
    assert params.sizeGreaterThanOrEquals(2);
    var items = MutableArrayList.<Term>create(params.size());
    record Item(@NotNull CoeTerm coe, @NotNull Arg<Term> arg) {
      public @NotNull Term fill(@NotNull LocalVar i) {
        return AppTerm.make(CoeTerm.coeFill(coe.type(), coe.restr(), new RefTerm(i)), arg);
      }
      public @NotNull Term app() {
        return AppTerm.make(coe, arg);
      }
    }
    var subst = new Subst();

    var ix = 1;
    for (var param : params) {
      // Item: t.ix
      var tn = new ProjTerm(t, ix++);
      // Because i : I |- params, so is i : I |- param, now bound An := \i. param
      var An = new LamTerm(new Param(i, I, true), param.type().subst(subst));
      // An.coe(Fill) t.ix
      var item = new Item(new CoeTerm(An, coe.restr()), new Arg<>(tn, true));
      // Substitution will take care of renaming
      subst.add(param.ref(), item.fill(i));
      items.append(item.app());
    }
    return new LamTerm(BetaExpander.coeDom(t.var(), coe.type()),
      new TupTerm(items.toImmutableArray()));
  }
}

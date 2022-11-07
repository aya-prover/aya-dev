// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.visitor.BetaExpander;
import org.aya.generic.Arg;
import org.aya.generic.SortKind;
import org.aya.ref.LocalVar;
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

  public @NotNull LamTerm coe(CoeTerm coe, LocalVar varI) {
    var u0Var = new LocalVar("u0");
    var A = new LamTerm(new Param(varI, IntervalTerm.INSTANCE, true), params.first().type());

    var B = params().sizeEquals(2) ?
      new LamTerm(new Param(varI, IntervalTerm.INSTANCE, true), params.get(1).type()) :
      new LamTerm(new Param(varI, IntervalTerm.INSTANCE, true), new SigmaTerm(params.drop(1)));

    var u00 = new ProjTerm(new RefTerm(u0Var), 1);
    var u01 = new ProjTerm(new RefTerm(u0Var), 2);
    var v = AppTerm.make(CoeTerm.coeFill(A, coe.restr(), new RefTerm(varI)), new Arg<>(u00, true));

    var Bsubsted = B.subst(params().first().ref(), v);
    var coe0 = AppTerm.make(new CoeTerm(A, coe.restr()), new Arg<>(u00, true));
    var coe1 = AppTerm.make(new CoeTerm(Bsubsted, coe.restr()), new Arg<>(u01, true));
    return new LamTerm(BetaExpander.coeDom(u0Var, coe.type()), new TupTerm(ImmutableSeq.of(coe0, coe1)));
  }
}

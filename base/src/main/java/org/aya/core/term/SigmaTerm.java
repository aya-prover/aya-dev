// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.core.visitor.BetaExpander;
import org.aya.core.visitor.Subst;
import org.aya.generic.SortKind;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * @author re-xyr
 */
public record SigmaTerm(@NotNull ImmutableSeq<@NotNull Param> params) implements StableWHNF, Formation {
  public @NotNull SigmaTerm update(@NotNull ImmutableSeq<Param> params) {
    return params.sameElements(params(), true) ? this : new SigmaTerm(params);
  }

  @Override public @NotNull SigmaTerm descent(@NotNull UnaryOperator<@NotNull Term> f) {
    return update(params.map(p -> p.descent(f)));
  }

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

  public @NotNull LamTerm coe(@NotNull CoeTerm coe, @NotNull LocalVar i) {
    var t = new RefTerm(new LocalVar("t"));
    assert params.sizeGreaterThanOrEquals(2);
    var items = MutableArrayList.<Arg<Term>>create(params.size());
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
      var An = new LamTerm(new LamTerm.Param(i, true), param.type().subst(subst));
      // An.coe(Fill) t.ix
      var item = new Item(new CoeTerm(An, coe.restr()), new Arg<>(tn, true));
      // Substitution will take care of renaming
      subst.add(param.ref(), item.fill(i));
      items.append(new Arg<>(item.app(), param.explicit()));
    }
    return new LamTerm(BetaExpander.coeDom(t.var()),
      new TupTerm(items.toImmutableArray()));
  }

  /**
   * A simple "generalized type checking" for tuples.
   *
   * @return null if "too many items" error occur
   */
  public <T> @Nullable TupTerm check(@NotNull ImmutableSeq<? extends T> it, @NotNull BiFunction<T, Term, Term> inherit) {
    var items = MutableList.<Arg<Term>>create();
    var againstTele = params.view();
    var subst = new Subst(MutableMap.create());
    for (var iter = it.iterator(); iter.hasNext(); ) {
      var item = iter.next();
      var first = againstTele.first().subst(subst);
      var result = inherit.apply(item, first.type());
      items.append(new Arg<>(result, first.explicit()));
      var ref = first.ref();
      againstTele = againstTele.drop(1);
      if (againstTele.isNotEmpty())
        // LGTM! The show must go on
        subst.add(ref, result);
      else if (iter.hasNext())
        // Too many items
        return null;
    }
    return new TupTerm(items.toImmutableArray());
  }
}

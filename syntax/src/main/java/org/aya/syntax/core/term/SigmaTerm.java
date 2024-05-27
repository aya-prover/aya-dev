// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Result;
import kala.function.IndexedFunction;
import org.aya.generic.term.SortKind;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * @author re-xyr
 */
public record SigmaTerm(@NotNull ImmutableSeq<Term> params) implements StableWHNF, Formation {
  public @NotNull SigmaTerm update(@NotNull ImmutableSeq<Term> params) {
    return params.sameElements(params(), true) ? this : new SigmaTerm(params);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(params.mapIndexed(f));
  }

  public static @NotNull SortTerm lub(@NotNull SortTerm x, @NotNull SortTerm y) {
    int lift = Math.max(x.lift(), y.lift());
    return x.kind() == SortKind.Set || y.kind() == SortKind.Set
      ? new SortTerm(SortKind.Set, lift)
      : x.kind() == SortKind.Type || y.kind() == SortKind.Type
        ? new SortTerm(SortKind.Type, lift)
        : x.kind() == SortKind.ISet || y.kind() == SortKind.ISet
          // ice: this is controversial, but I think it's fine.
          // See https://github.com/agda/cubical/pull/910#issuecomment-1233113020
          ? SortTerm.ISet : Panic.unreachable();
  }

  // public @NotNull LamTerm coe(@NotNull CoeTerm coe, @NotNull LamTerm.Param i) {
  //   var t = new RefTerm(new LocalVar("t"));
  //   assert params.sizeGreaterThanOrEquals(2);
  //   var items = MutableArrayList.<Arg<Term>>create(params.size());
  //   var subst = new Subst();
  //
  //   var ix = 1;
  //   for (var param : params) {
  //     // Item: t.ix
  //     var tn = new ProjTerm(t, ix++);
  //     // Because i : I |- params, so is i : I |- param, now bound An := \i. param
  //     var An = new LamTerm(i, param.type().subst(subst)).rename();
  //     // coe r s' (\i => A_n) t.ix
  //     UnaryOperator<Term> fill = s -> AppTerm.make(new CoeTerm(An, coe.r(), s),
  //       new Arg<>(tn, true));
  //
  //     subst.add(param.ref(), fill.apply(i.toTerm()));
  //     items.append(new Arg<>(fill.apply(coe.s()), param.explicit()));
  //   }
  //   return new LamTerm(new LamTerm.Param(t.var(), true),
  //     new TupTerm(items.toImmutableArray()));
  // }

  @FunctionalInterface
  public interface Checker<T> extends BiFunction<@NotNull T, @NotNull Term, @Nullable Term> {
  }

  /**
   * A simple "generalized type checking" for tuples.
   */
  public <T> @NotNull Result<ImmutableSeq<Term>, ErrorKind>
  check(@NotNull ImmutableSeq<? extends T> it, @NotNull Checker<T> checker) {
    return check(it.iterator(), checker);
  }

  public <T> @NotNull Result<ImmutableSeq<Term>, ErrorKind>
  check(@NotNull Iterator<? extends T> iter, @NotNull Checker<T> checker) {
    var args = MutableList.<Term>create();
    Iterator<@Nullable Term> params = view(t -> {
      var result = checker.apply(iter.next(), t);
      if (result != null) args.append(result);
      return result;
    }).iterator();

    while (iter.hasNext() && params.hasNext()) {
      // param.next() calls iter.next()
      if (params.next() == null) return Result.err(ErrorKind.CheckFailed);
    }
    // if each call to params.next() returns non-null, then they must be all added to args

    if (iter.hasNext()) return Result.err(ErrorKind.TooManyElement);
    if (params.hasNext()) return Result.err(ErrorKind.TooManyParameter);
    return Result.ok(args.toImmutableSeq());
  }

  // ruast!
  public enum ErrorKind {
    TooManyElement,
    TooManyParameter,
    CheckFailed
  }

  @NotNull public SeqView<Term> view(@NotNull UnaryOperator<Term> putIndex) {
    return new SeqView<>() {
      @Override public @NotNull Iterator<Term> iterator() {
        return new Iterator<>() {
          private final @NotNull MutableList<Term> args = MutableList.create();
          private final @NotNull Iterator<Term> paramIter = params.iterator();
          @Override public boolean hasNext() {
            return paramIter.hasNext();
          }
          @Override public Term next() {
            var result = paramIter.next().instantiateTele(args.view());
            args.append(putIndex.apply(result));
            return result;
          }
        };
      }
    };
  }
}

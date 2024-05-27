// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.term.AppTerm;
import org.aya.syntax.core.term.PiTerm;
import org.aya.syntax.core.term.SortTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.TyckInternal;
import org.aya.syntax.ref.MetaVar;
import org.jetbrains.annotations.NotNull;

/**
 * @param args can grow!! See {@link AppTerm#make()}
 */
public record MetaCall(
  @NotNull MetaVar ref,
  @Override @NotNull ImmutableSeq<Term> args
) implements Callable, TyckInternal {
  public static @NotNull Term app(MetaVar ref, Term rhs, ImmutableSeq<Term> args) {
    var directArgs = args.sliceView(0, ref.ctxSize());
    var restArgs = args.sliceView(ref.ctxSize(), args.size());
    return AppTerm.make(rhs.instantiateTele(directArgs), restArgs);
  }
  public static @NotNull Term appType(MetaVar ref, Term rhsType, ImmutableSeq<Term> args) {
    var directArgs = args.sliceView(0, ref.ctxSize());
    var restArgs = args.sliceView(ref.ctxSize(), args.size());
    return PiTerm.substBody(rhsType.instantiateTele(directArgs), restArgs);
  }

  public @NotNull MetaCall asPiDom(@NotNull SortTerm result) {
    return ref.asPiDom(result, args);
  }

  public @NotNull Term update(@NotNull ImmutableSeq<Term> args) {
    return args.sameElements(args(), true) ? this : new MetaCall(ref, args);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(Callable.descent(args, f));
  }
}

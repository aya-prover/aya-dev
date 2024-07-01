// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.def.ClassDefLike;
import org.aya.syntax.core.def.MemberDefLike;
import org.aya.syntax.core.term.NewTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ClassCall is a very special construction in Aya.
 * <ul>
 *   <li>It is like a type when partially instantiated -- the type of "specifications" of the rest of the fields.</li>
 *   <li>It is like a term when fully instantiated, whose type can be anything.</li>
 *   <li>It can be applied like a function, which essentially inserts the nearest missing field.</li>
 * </ul>
 *
 * As the type of some class instance {@link NewTerm}, the {@param args} may refer to other member
 * (not just former members!), therefore the value of members depend on the class instance.
 * In order to check a class instance against to a ClassCall, you need to supply the class instance
 * to obtain the actual arguments of the ClassCall, see {@link #args(Term)}
 *
 * @author kiva, ice1000
 */
public record ClassCall(
  @NotNull ClassDefLike ref,
  @Override int ulift,
  @NotNull ImmutableSeq<Closure> args
) implements StableWHNF, Formation {
  public @NotNull ImmutableSeq<Term> args(@NotNull Term self) {
    return this.args.map(x -> x.apply(self));
  }

  public @NotNull ClassCall update(@NotNull ImmutableSeq<Closure> args) {
    return this.args.sameElements(args, true)
      ? this : new ClassCall(ref, ulift, args);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(args.map(t -> t.descent(f)));
  }

  public @Nullable Closure get(@NotNull MemberDefLike member) {
    assert member.classRef() == ref;
    return args.getOrNull(member.index());
  }

  @Override public @NotNull Term doElevate(int level) {
    return new ClassCall(ref, ulift + level, args);
  }
}

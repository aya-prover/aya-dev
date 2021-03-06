// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.generic;

import org.aya.ref.LocalVar;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;


/**
 * @author kiva
 */
public sealed interface Pat<Term> {
  @Nullable LocalVar as();
  @NotNull Term type();

  <Term2> @NotNull Pat<Term2> mapTerm(@NotNull Function<Term, Term2> mapper);

  record PatAtom<Term>(
    @NotNull Atom<Pat<Term>> atom,
    @Nullable LocalVar as,
    @NotNull Term type
  ) implements Pat<Term> {
    @Override public @NotNull <Term2> PatAtom<Term2> mapTerm(@NotNull Function<Term, Term2> mapper) {
      return new PatAtom<>(atom.mapTerm(i -> i.mapTerm(mapper)),
        as, mapper.apply(type));
    }
  }

  record PatCtor<Term>(
    @NotNull String name,
    @NotNull Buffer<Pat<Term>> params,
    @Nullable LocalVar as,
    @NotNull Term type
  ) implements Pat<Term> {
    @Override public @NotNull <Term2> PatCtor<Term2> mapTerm(@NotNull Function<Term, Term2> mapper) {
      return new PatCtor<>(name,
        params.stream().map(i -> i.mapTerm(mapper)).collect(Buffer.factory()),
        as, mapper.apply(type));
    }
  }

  /**
   * pattern remains unresolved because we are unable to know
   * whether `zero` is a data ctor or a bind id
   */
  record UnresolvedPat<Term>(
    @NotNull Atom<Pat<Term>> name,
    @NotNull Buffer<Atom<Pat<Term>>> params,
    @Nullable LocalVar as,
    @NotNull Term type
  ) implements Pat<Term> {
    @Override public @NotNull <Term2> UnresolvedPat<Term2> mapTerm(@NotNull Function<Term, Term2> mapper) {
      return new UnresolvedPat<>(name.mapTerm(i -> i.mapTerm(mapper)),
        params.stream().map(j -> j.mapTerm(i -> i.mapTerm(mapper))).collect(Buffer.factory()),
        as, mapper.apply(type));
    }
  }

  /**
   * @author kiva
   */
  sealed interface Atom<Pat> {
    <Pat2> @NotNull Atom<Pat2> mapTerm(@NotNull Function<Pat, Pat2> mapper);

    record Tuple<Pat>(@NotNull Buffer<Pat> patterns) implements Atom<Pat> {
      @Override public @NotNull <Pat2> Tuple<Pat2> mapTerm(@NotNull Function<Pat, Pat2> mapper) {
        return new Tuple<Pat2>(patterns.stream().map(mapper).collect(Buffer.factory()));
      }
    }

    record Braced<Pat>(@NotNull Buffer<Pat> patterns) implements Atom<Pat> {
      @Override public @NotNull <Pat2> Braced<Pat2> mapTerm(@NotNull Function<Pat, Pat2> mapper) {
        return new Braced<Pat2>(patterns.stream().map(mapper).collect(Buffer.factory()));
      }
    }

    record Number<Pat>(int number) implements Atom<Pat> {
      @Override public @NotNull <Pat2> Number<Pat2> mapTerm(@NotNull Function<Pat, Pat2> mapper) {
        return new Number<>(number);
      }
    }

    record CalmFace<Pat>() implements Atom<Pat> {
      @Override public @NotNull <Pat2> CalmFace<Pat2> mapTerm(@NotNull Function<Pat, Pat2> mapper) {
        return new CalmFace<>();
      }
    }

    record Bind<Pat>(@NotNull LocalVar bind) implements Atom<Pat> {
      @Override public @NotNull <Pat2> Bind<Pat2> mapTerm(@NotNull Function<Pat, Pat2> mapper) {
        return new Bind<>(bind);
      }
    }
  }

  /**
   * @author kiva
   */
  sealed interface Clause<Term> {
    <P, R> R accept(@NotNull Visitor<Term, P, R> visitor, P p);

    interface Visitor<Term, P, R> {
      R visitMatch(@NotNull Match<Term> match, P p);
      R visitAbsurd(@NotNull Absurd<Term> absurd, P p);
    }

    record Match<Term>(
      @NotNull Buffer<Pat<Term>> patterns,
      @NotNull Term expr
    ) implements Clause<Term> {
      @Override public <P, R> R accept(@NotNull Visitor<Term, P, R> visitor, P p) {
        return visitor.visitMatch(this, p);
      }
    }

    record Absurd<Term>() implements Clause<Term> {
      @Override public <P, R> R accept(@NotNull Visitor<Term, P, R> visitor, P p) {
        return visitor.visitAbsurd(this, p);
      }
    }
  }
}

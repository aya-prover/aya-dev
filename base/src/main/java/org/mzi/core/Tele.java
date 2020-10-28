// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core;

import asia.kala.PrimitiveTuples.IntObjTuple2;
import asia.kala.Tuple;
import asia.kala.Tuple2;
import asia.kala.collection.Seq;
import asia.kala.collection.mutable.Buffer;
import asia.kala.function.IndexedConsumer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.mzi.api.ref.Bind;
import org.mzi.api.ref.Var;
import org.mzi.generic.Arg;

import java.util.function.BiConsumer;

/**
 * Similar to Arend <code>DependentLink</code>.
 * If we have <code>{A : U} (a b : A)</code>, then it should be translated into:
 * <pre>
 * {@link Tele.TypedTele}(A, {@link org.mzi.core.term.UnivTerm}, false,
 *   {@link Tele.NamedTele}(a, {@link Tele.TypedTele}(b, A, true, null)))
 * </pre>
 *
 * @author ice1000
 */
public interface Tele<Term> extends Bind<Term> {
  @Override @Nullable Tele<Term> next();
  @Override @NotNull Term type();

  <P, R> R accept(@NotNull Tele.Visitor<Term, P, R> visitor, P p);
  <P, Q, R> R accept(@NotNull Tele.BiVisitor<Term, P, Q, R> visitor, P p, Q q);

  default @NotNull Buffer<@NotNull Tele<Term>> toBuffer() {
    var buf = Buffer.<Tele<Term>>of();
    forEach((i, x) -> buf.append(x));
    return buf;
  }

  default @NotNull IntObjTuple2<@NotNull Tele<Term>>
  forEach(@NotNull IndexedConsumer<@NotNull Tele<Term>> consumer) {
    var tele = this;
    var i = 0;
    while (true) {
      consumer.accept(i++, tele);
      if (tele.next() == null) break;
      else tele = tele.next();
    }
    return new IntObjTuple2<>(i, tele);
  }

  /**
   * @param <T>      term or expr
   * @param consumer that traverses the telescopes
   * @return a tuple containing the lhs and rhs left.
   */
  static @NotNull <T> Tuple2<@Nullable Tele<T>, @Nullable Tele<T>> biForEach(
    @NotNull Tele<T> lhs, @NotNull Tele<T> rhs,
    @NotNull BiConsumer<@NotNull Tele<T>, @NotNull Tele<T>> consumer
  ) {
    while (lhs != null && rhs != null) {
      consumer.accept(lhs, rhs);
      lhs = lhs.next();
      rhs = rhs.next();
    }
    return Tuple.of(lhs, rhs);
  }

  default @NotNull Tele<Term> last() {
    return forEach((index, tele) -> {})._2;
  }

  default int size() {
    return forEach((index, tele) -> {})._1;
  }

  @TestOnly @Contract(pure = true)
  default boolean checkSubst(@NotNull Seq<@NotNull Arg<Term>> args) {
    var obj = new Object() {
      boolean ok = true;
    };
    forEach((i, tele) -> obj.ok = obj.ok && tele.explicit() == args.get(i).explicit());
    return obj.ok;
  }

  interface Visitor<Term, P, R> {
    R visitTyped(@NotNull TypedTele<Term> typed, P p);
    R visitNamed(@NotNull NamedTele<Term> named, P p);
  }

  interface BiVisitor<Term, P, Q, R> {
    R visitTyped(@NotNull TypedTele<Term> typed, P p, Q q);
    R visitNamed(@NotNull NamedTele<Term> named, P p, Q q);
  }

  record TypedTele<Term>(
    @NotNull Var ref,
    @NotNull Term type,
    boolean explicit,
    @Nullable Tele<Term> next
  ) implements Tele<Term> {
    @Override public <P, R> R accept(@NotNull Visitor<Term, P, R> visitor, P p) {
      return visitor.visitTyped(this, p);
    }

    @Override public <P, Q, R> R accept(@NotNull BiVisitor<Term, P, Q, R> visitor, P p, Q q) {
      return visitor.visitTyped(this, p, q);
    }
  }

  /**
   * @author ice1000
   */
  record NamedTele<Term>(
    @NotNull Var ref,
    @NotNull Tele<Term> next
  ) implements Tele<Term> {
    @Contract(pure = true) @Override public boolean explicit() {
      return next().explicit();
    }

    @Contract(pure = true) @Override public @NotNull Term type() {
      return next().type();
    }

    @Override public <P, R> R accept(@NotNull Visitor<Term, P, R> visitor, P p) {
      return visitor.visitNamed(this, p);
    }

    @Override public <P, Q, R> R accept(@NotNull BiVisitor<Term, P, Q, R> visitor, P p, Q q) {
      return visitor.visitNamed(this, p, q);
    }
  }
}

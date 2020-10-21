package org.mzi.core.term;

import asia.kala.Tuple;
import asia.kala.Tuple2;
import asia.kala.collection.Seq;
import asia.kala.function.IndexedConsumer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.mzi.api.core.ref.CoreBind;
import org.mzi.api.ref.Ref;
import org.mzi.core.subst.TermSubst;
import org.mzi.generic.Arg;

import java.util.HashMap;

/**
 * Similar to Arend <code>DependentLink</code>.
 * If we have <code>{A : Type} (a b : A)</code>, then it should be translated into:
 * <pre>
 * {@link TypedTele}(A, {@link org.mzi.core.term.UnivTerm}, false,
 *   {@link NamedTele}(a, {@link TypedTele}(b, A, true, null)))
 * </pre>
 *
 * @author ice1000
 */
public sealed interface Tele extends CoreBind {
  @Override @Nullable Tele next();
  @Override @NotNull Term type();

  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

  default @NotNull Tuple2<Integer, Tele> forEach(@NotNull IndexedConsumer<@NotNull Tele> consumer) {
    var tele = this;
    var i = 0;
    while (true) {
      consumer.accept(i++, tele);
      if (tele.next() == null) break;
      else tele = tele.next();
    }
    return Tuple.of(i, tele);
  }

  default @NotNull Tele last() {
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

  @Contract(pure = true)
  default @NotNull TermSubst buildSubst(@NotNull Seq<@NotNull Arg<Term>> args) {
    var subst = new TermSubst(new HashMap<>());
    forEach((i, tele) -> subst.add(tele.ref(), args.get(i).term()));
    return subst;
  }

  interface Visitor<P, R> {
    R visitTyped(@NotNull TypedTele typed, P p);
    R visitNamed(@NotNull NamedTele named, P p);
  }

  record TypedTele(
    @NotNull Ref ref,
    @NotNull Term type,
    boolean explicit,
    @Nullable Tele next
  ) implements Tele {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTyped(this, p);
    }
  }

  /**
   * @author ice1000
   */
  record NamedTele(
    @NotNull Ref ref,
    @NotNull Tele next
  ) implements Tele {
    @Contract(pure = true) @Override public boolean explicit() {
      return next().explicit();
    }

    @Contract(pure = true) @Override public @NotNull Term type() {
      return next().type();
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitNamed(this, p);
    }
  }
}

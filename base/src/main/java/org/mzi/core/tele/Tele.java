package org.mzi.core.tele;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.core.ref.CoreBind;
import org.mzi.api.ref.Ref;
import org.mzi.core.term.Term;

/**
 * @author ice1000
 * <p>
 * Similar to Arend <code>DependentLink</code>.
 * If we have <code>{A : Type} (a b : A)</code>, then it should be translated into:
 * <pre>
 * {@link TypedTele}(A, false, {@link org.mzi.core.term.UnivTerm},<br/>
 *   {@link NamedTele}(a, {@link TypedTele}(b, true, A, null)))
 * </pre>
 */
public sealed interface Tele extends CoreBind {
  @Override @Nullable Tele next();
  @Override @NotNull Term type();

  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

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

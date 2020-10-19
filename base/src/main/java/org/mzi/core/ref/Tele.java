package org.mzi.core.ref;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.core.ref.CoreBind;
import org.mzi.api.ref.Ref;
import org.mzi.core.term.Term;

/**
 * @author ice1000
 *
 * Similar to Arend <code>DependentLink</code>.
 */
public interface Tele extends CoreBind {
  @Override @Nullable Tele next();

  record TypedTele(
    @NotNull Ref ref,
    @NotNull Term type,
    @Nullable Tele next,
    boolean explicit
  ) {
  }

  /**
   * @author ice1000
   */
  record NameTele(
    @NotNull Ref ref,
    @Nullable Tele next,
    boolean explicit
  ) implements Tele {
  }
}

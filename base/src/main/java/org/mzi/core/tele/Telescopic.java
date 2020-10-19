package org.mzi.core.tele;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface Telescopic {
  @Contract(pure = true) @NotNull Tele telescope();

  default @NotNull Tele first() {
    return telescope();
  }

  default @NotNull Tele last() {
    var tele = telescope();
    while (tele.next() != null) tele = tele.next();
    return tele;
  }
}

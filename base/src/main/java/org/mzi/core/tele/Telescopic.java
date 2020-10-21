package org.mzi.core.tele;

import asia.kala.Tuple;
import asia.kala.Tuple2;
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

  private static @NotNull Tuple2<@NotNull Integer, @NotNull Tele> lastInfo(@NotNull Telescopic telescopic) {
    var tele = telescopic.telescope();
    var i = 0;
    while (tele.next() != null) {
      tele = tele.next();
      i++;
    }
    return Tuple.of(i, tele);
  }

  default @NotNull Tele last() {
    return lastInfo(this)._2;
  }

  default int size() {
    return lastInfo(this)._1 + 1;
  }
}

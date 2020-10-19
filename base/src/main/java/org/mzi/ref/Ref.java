package org.mzi.ref;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.CoreRef;

/**
 * @author ice1000
 */
public interface Ref extends CoreRef {
  record LocalRef(@NotNull String name) implements Ref {
  }
}

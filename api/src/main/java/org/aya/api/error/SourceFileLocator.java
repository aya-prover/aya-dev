// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import org.glavo.kala.collection.SeqLike;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface SourceFileLocator {
  /**
   * Resolve absolute source file path to module-root.
   * for example, assuming we have a module root `/path/to/root`,
   * resolving the source file path `/path/to/root/A/B/C.aya`
   * should result in `A/B/C.aya`
   *
   * @param path Path to source file
   * @return relativized file path if it belongs to a module, otherwise the original path is returned
   */
  default @NotNull String locate(@NotNull Path path) {
    return path.toString();
  }

  record Module(@NotNull SeqLike<Path> modulePath) implements SourceFileLocator {
    @Override public @NotNull String locate(@NotNull Path path) {
      var normalized = path.toAbsolutePath().normalize();
      var found = modulePath.find(m -> normalized.startsWith(m.toAbsolutePath()));
      if (found.isDefined()) return found.get().toAbsolutePath().normalize().relativize(normalized).toString();
      return path.toString();
    }
  }
}

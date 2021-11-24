// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.CountingReporter;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * @author ice1000
 */
public interface LibraryOwner {
  int DEFAULT_INDENT = 2;
  /** @return Source dirs of this module, out dirs of all dependencies. */
  @NotNull SeqView<Path> modulePath();
  /** @return Out dir of this module. */
  @NotNull Path outDir();
  @NotNull CountingReporter reporter();
  @NotNull LibrarySource findModuleFile(@NotNull ImmutableSeq<String> mod);
}

// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli.library;

import org.aya.util.Version;
import org.glavo.kala.collection.immutable.ImmutableSeq;

import java.nio.file.Path;

/**
 * @param ayaVersion   version used to compile this library
 * @param outDir       output dir of this library
 * @param srcDirs      src dirs of this library (correspond to SourceFileLocator)
 * @param libraryPaths search libraries from these dirs when compiling this library
 * @author re-xyr
 * @implNote <a href="https://github.com/ice1000/aya-prover/issues/491">issue #491</a>
 */
public record LibraryConfig(
  Version ayaVersion,
  Path outDir,
  ImmutableSeq<Path> srcDirs,
  ImmutableSeq<Path> libraryPaths
) {
}

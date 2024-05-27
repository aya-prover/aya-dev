// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.util;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSeq;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * TODO: move this to somewhere else, compiled aya should not depends on this module
 * Some tools that the serialized code may use
 */
public class SerializeUtils {
  /**
   * Copy the sequence {@param source} to {@param dest}, starts at {@code dest[from]}
   */
  public static void copyTo(@NotNull MutableSeq<Term> dest, @NotNull ImmutableSeq<Term> source, int from) {
    source.forEachIndexed((idx, term) -> dest.set(from + idx, term));
  }
}

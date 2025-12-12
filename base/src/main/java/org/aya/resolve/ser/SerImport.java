// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.ser;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.ref.ModulePath;
import org.jetbrains.annotations.NotNull;

/**
 * @param rename not empty
 */
public record SerImport(
  @NotNull ModulePath path, @NotNull ImmutableSeq<String> rename,
  boolean isPublic) implements SerCommand { }

// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.utils;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Special gift for Intellij IDEA plugin. Usually used in response models to skip json serialization of certain data.
 * @see org.aya.lsp.models.HighlightResult.Symbol
 */
public final class ForIDEA<T> implements Serializable {
  public transient @NotNull T value;

  public ForIDEA(@NotNull T value) {
    this.value = value;
  }
}

// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat.iter;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/// ~~Some {@link #peek() head} can be extracted from a Pusheenable and leave the body alone~~
/// A Pusheenable is a functional list.
public interface Pusheenable<T, R> extends Iterator<T> {
  @NotNull T peek();

  @NotNull R body();
}

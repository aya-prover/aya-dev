// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@SuppressWarnings("unchecked")
public interface ArrayUtil {
  static <A> void fill(A @NotNull [] @NotNull [] a, @NotNull A value) {
    for (var as : a) Arrays.fill(as, value);
  }

  static <A, B> @NotNull Tuple2<A, B> @NotNull [] zip(A @NotNull [] a, B @NotNull [] b) {
    var len = Math.min(a.length, b.length);
    var c = new Tuple2[len];
    for (int i = 0; i < len; i++) c[i] = Tuple.of(a[i], b[i]);
    return c;
  }
}

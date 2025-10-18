// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.function.Function;

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

  static <A, B> B @NotNull [] map(A @NotNull [] array, B @NotNull [] outTyper, Function<A, B> f) {
    var b = (B[]) Array.newInstance(outTyper.getClass().componentType(), array.length);
    for (int i = 0; i < array.length; i++) b[i] = f.apply(array[i]);
    return b;
  }
}

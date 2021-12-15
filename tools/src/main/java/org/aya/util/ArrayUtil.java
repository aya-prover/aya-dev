// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Tuple3;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public interface ArrayUtil {
  static <A> void fill(A @NotNull [] @NotNull [] a, @NotNull A value) {
    for (var as : a) Arrays.fill(as, value);
  }

  static <A> A @NotNull [] map(A @NotNull [] array, Function<A, A> f) {
    var a = (A[]) Array.newInstance(array.getClass().componentType(), array.length);
    for (int i = 0; i < array.length; i++) a[i] = f.apply(array[i]);
    return a;
  }

  static <A, B> B @NotNull [] map(A @NotNull [] array, B @NotNull [] outTyper, Function<A, B> f) {
    var b = (B[]) Array.newInstance(outTyper.getClass().componentType(), array.length);
    for (int i = 0; i < array.length; i++) b[i] = f.apply(array[i]);
    return b;
  }

  static <A> A @NotNull [] map(A @NotNull [] array, Function<A, A> f, int start, int end) {
    var a = (A[]) Array.newInstance(array.getClass().componentType(), end - start);
    for (int i = start; i < end; i++) a[i - start] = f.apply(array[i]);
    return a;
  }

  static <A> A @NotNull [] map(A @NotNull [] array, Function<A, A> f, int start) {
    return map(array, f, start, array.length);
  }

  static <A, B> @NotNull Tuple2<A, B> @NotNull [] zip(A @NotNull [] a, B @NotNull [] b) {
    var len = Math.min(a.length, b.length);
    var c = new Tuple2[len];
    for (int i = 0; i < len; i++) c[i] = Tuple.of(a[i], b[i]);
    return c;
  }

  static <A, B, C> @NotNull Tuple3<A, B, C> @NotNull [] zip(A @NotNull [] a, B @NotNull [] b, C @NotNull [] c) {
    var len = Math.min(a.length, Math.min(b.length, c.length));
    var d = new Tuple3[len];
    for (int i = 0; i < len; i++) d[i] = Tuple.of(a[i], b[i], c[i]);
    return d;
  }

  static <A> boolean identical(A @NotNull [] a, A @NotNull [] b) {
    if (a.length != b.length) return false;
    for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
    return true;
  }
}

// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import kala.function.CheckedRunnable;
import org.jetbrains.annotations.NotNull;

public class Profiler {
  public static long profile(@NotNull CheckedRunnable<?> runnable) {
    var begin = System.currentTimeMillis();
    runnable.run();
    var end = System.currentTimeMillis();
    return end - begin;
  }

  public static long profileMany(String title, int count, @NotNull CheckedRunnable<?> runnable) {
    assert count > 0;
    System.out.println("Profiling " + title);

    long[] times = new long[count];
    long begin, end;

    for (int i = 0; i < count; ++i) {
      begin = System.currentTimeMillis();
      runnable.run();
      end = System.currentTimeMillis();
      times[i] = end - begin;
    }

    long sum = 0;
    for (var i = 0; i < count; ++i) {
      var time = times[i];
      sum += time;
      System.out.println(i + ": Done in " + time + "ms");
    }

    var ave = sum / count;
    System.out.println("Average: Done in " + ave + "ms");
    return ave;
  }
}

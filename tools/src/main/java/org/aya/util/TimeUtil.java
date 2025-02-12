// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import kala.function.CheckedRunnable;
import org.jetbrains.annotations.NotNull;

public interface TimeUtil {
  /** <a href="https://github.com/JetBrains/Arend/blob/39b14869ac5abdcee7bbee0efa06d5a6f86c4069/cli/src/main/java/org/arend/frontend/library/TimedLibraryManager.java#L21-L31">Arend</a> */
  static @NotNull String millisToString(long time) {
    if (time < 10000) return time + "ms";
    if (time < 60000) return time / 1000 + ("." + (time / 100 % 10)) + "s";
    var seconds = time / 1000;
    return (seconds / 60) + "m" + (seconds % 60) + "s";
  }

  static @NotNull String gitFormat() {
    return ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss X"));
  }

  static long profile(@NotNull CheckedRunnable<?> runnable) {
    var begin = System.currentTimeMillis();
    runnable.run();
    var end = System.currentTimeMillis();
    return end - begin;
  }

  static double profileMany(String title, int count, @NotNull CheckedRunnable<?> runnable) {
    assert count > 0;
    System.out.println("Profiling " + title);

    var times = new long[count];
    for (int i = 0; i < count; ++i) {
      var time = profile(runnable);
      times[i] = time;
      System.out.println(i + ": Done in " + millisToString(time));
    }

    var stats = Arrays.stream(times).summaryStatistics();
    System.out.println("Average: Done in " + millisToString((long) stats.getAverage()));
    return stats.getAverage();
  }
}

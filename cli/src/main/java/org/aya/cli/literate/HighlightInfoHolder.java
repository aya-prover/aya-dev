// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import org.jetbrains.annotations.NotNull;

import java.util.PriorityQueue;

public class HighlightInfoHolder {
  /**
   * In fact, we should consider these cases:
   * <ul>
   *   <li>{@code <b>a</b>, <a>b</a>}</li>
   *   <li>{@code <b>a, <a>b</a></b>}</li>
   *   <li>{@code <b>a, <a> b</b>, c</a>} which is illegal, we won't support it</li>
   * </ul>
   */
  private final @NotNull PriorityQueue<HighlightInfo> queue;

  public HighlightInfoHolder() {
    this.queue = new PriorityQueue<>(HighlightInfo.Ord.INSTANCE);
  }

  public void addInfo(@NotNull HighlightInfo info) {
    queue.add(info);
  }

  public @NotNull PriorityQueue<HighlightInfo> getQueue() {
    return queue;
  }
}

// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.serde;

import java.io.Serializable;
import java.util.List;

/**
 * @author ice1000
 */
public sealed interface SerLevel extends Serializable {
  /** @param num -1 means infinity */
  record Const(int num) implements SerLevel {
  }

  record Ref(long id, int lift) implements SerLevel {
  }

  record Max(List<SerLevel> levels) implements Serializable {
  }
}

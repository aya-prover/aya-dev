// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import org.aya.tyck.unify.level.LevelSolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LevelSolverRandomTest {
  @Test public void markdownify() {
    assertNotNull(LevelSolver.markdownify(new int[][]{
      new int[]{1, 1, 4, 5, 1, 4}
    }));
  }
}

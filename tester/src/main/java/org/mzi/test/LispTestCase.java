// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.test;

import asia.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mzi.api.error.CollectReporter;
import org.mzi.api.ref.Var;
import org.mzi.tyck.sort.LevelEqn;
import org.mzi.tyck.unify.NaiveDefEq;
import org.mzi.util.Ordering;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LispTestCase {
  protected final Map<String, @NotNull Var> vars = new HashMap<>();
  protected final CollectReporter reporter = new CollectReporter();

  protected @NotNull NaiveDefEq eq() {
    return new NaiveDefEq(Ordering.Eq, new LevelEqn.Set(reporter, Buffer.of(), Buffer.of()));
  }

  @BeforeEach
  public void clearVars() {
    vars.clear();
  }

  @AfterEach
  public void assertNoErrors() {
    assertTrue(reporter.errors().isEmpty());
  }
}

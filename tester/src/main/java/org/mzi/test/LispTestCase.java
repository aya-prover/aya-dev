// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.test;

import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mzi.api.error.CollectReporter;
import org.mzi.api.ref.Var;
import org.mzi.core.term.Term;
import org.mzi.tyck.MetaContext;
import org.mzi.tyck.unify.TypeDirectedDefEq;
import org.mzi.tyck.unify.TypedDefEq;
import org.mzi.util.Ordering;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LispTestCase {
  protected final Map<String, @NotNull Var> vars = new HashMap<>();
  protected final CollectReporter reporter = new CollectReporter();

  protected @NotNull TypeDirectedDefEq eq(MutableMap<Var, Term> localCtx) {
    return new TypeDirectedDefEq(eq -> new TypedDefEq.PatDefEq(eq, Ordering.Eq, new MetaContext(reporter)), localCtx);
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

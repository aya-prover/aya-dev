// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mzi.api.ref.Var;
import org.mzi.concrete.parse.MziProducer;
import org.mzi.concrete.resolve.context.EmptyContext;
import org.mzi.core.def.FnDef;
import org.mzi.test.Lisp;
import org.mzi.test.ThrowingReporter;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class TyckDeclTest {
  @Test
  public void testIdFunc() {
    idFuncTestCase("\\def id {A : \\Set} (a : A) : A => a");
    idFuncTestCase("\\def id {A : \\Set} (a : A) => a");
  }

  public void idFuncTestCase(@NotNull @NonNls @Language("TEXT") String code) {
    var fnDef = successTyckFn(code);
    var vars = new HashMap<String, @NotNull Var>();
    vars.put(fnDef.ref().name(), fnDef.ref());
    var expected = Lisp.parseDef("id",
      "(A (U) im (a (A) ex null))", "A", "a", vars);
    assertEquals(expected, fnDef);
  }

  private FnDef successTyckFn(@NotNull @NonNls @Language("TEXT") String code) {
    var decl = MziProducer.parseDecl(code);
    decl.ctx = new EmptyContext(ThrowingReporter.INSTANCE).derive();
    decl.resolve();
    var def = decl.tyck(ThrowingReporter.INSTANCE);
    assertNotNull(def);
    assertTrue(def instanceof FnDef);
    return ((FnDef) def);
  }
}

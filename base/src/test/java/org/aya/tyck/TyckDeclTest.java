// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.ref.Var;
import org.aya.concrete.parse.AyaProducer;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.core.def.FnDef;
import org.aya.test.Lisp;
import org.aya.test.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

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
    var decl = AyaProducer.parseDecl(code)._1;
    decl.ctx = new EmptyContext(ThrowingReporter.INSTANCE).derive();
    decl.resolve();
    var def = decl.tyck(ThrowingReporter.INSTANCE, null);
    assertNotNull(def);
    assertTrue(def instanceof FnDef);
    return ((FnDef) def);
  }
}

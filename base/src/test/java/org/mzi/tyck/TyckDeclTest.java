// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mzi.concrete.Decl;
import org.mzi.concrete.parse.MziProducer;
import org.mzi.concrete.resolve.context.SimpleContext;
import org.mzi.test.ThrowingReporter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TyckDeclTest {
  @Test
  public void testIdFunc() {
    var decl = tyckDecl("\\def xx {A, B : \\Set} (a : A) : A => a");
    assertTrue(decl instanceof Decl.FnDecl);
    Decl.FnDecl fnDecl = ((Decl.FnDecl) decl);
    assertNotNull(fnDecl.wellTyped);
  }

  private Decl tyckDecl(@NotNull @NonNls @Language("TEXT") String code) {
    var decl = MziProducer.parseDecl(code);
    decl.resolve(new SimpleContext());
    decl.desugar();
    decl.tyck(ThrowingReporter.INSTANCE);
    return decl;
  }
}

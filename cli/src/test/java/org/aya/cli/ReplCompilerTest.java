// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.ReplCompiler;
import org.aya.generic.util.NormalizeMode;
import org.aya.ref.AnyVar;
import org.aya.util.reporter.IgnoringReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReplCompilerTest {
  public final @NotNull ReplCompiler compiler = new ReplCompiler(ImmutableSeq.empty(), IgnoringReporter.INSTANCE, null);

  @BeforeEach public void setup() {
    var ctx = compiler.getContext();
    ctx.modules.clear();
    ctx.exports.clear();
    ctx.definitions.clear();
  }

  @Test
  public void issue382() {
    // success cases, we can find the definition in the context
    compile("data Nat | zero | suc Nat");
    var nat = findContext("Nat");
    assertNotNull(nat);

    // failure cases, the context is unchanged
    compile("data Nat =");
    var newNat = findContext("Nat");
    assertEquals(nat, newNat);

    compile("def a a");
    assertNull(findContext("a"));
  }

  private @Nullable AnyVar findContext(@NotNull String name) {
    var ctx = compiler.getContext();
    var def = ctx.definitions.getOrNull(name);
    if (def == null) return null;
    return def.getOrNull(ImmutableSeq.empty());
  }

  private void compile(@NotNull String code) {
    compiler.compileToContext(code, NormalizeMode.NF);
  }
}

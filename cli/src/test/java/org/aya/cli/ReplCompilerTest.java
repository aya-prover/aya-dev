// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.ReplCompiler;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.generic.Constants;
import org.aya.generic.util.NormalizeMode;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.ref.AnyVar;
import org.aya.resolve.context.Context;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.IgnoringReporter;
import org.aya.util.reporter.ThrowingReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ReplCompilerTest {
  public final @NotNull ReplCompiler compiler = new ReplCompiler(ImmutableSeq.empty(), new ThrowingReporter(AyaPrettierOptions.debug()), null);

  @BeforeEach public void setup() {
    var ctx = compiler.getContext();
    ctx.modules.clear();
    ctx.exports.clear();
    ctx.definitions.clear();
  }

  @Test public void library() throws IOException {
    compiler.loadToContext(Path.of("../lsp", "src", "test", "resources", "lsp-test-lib"));
    assertNotNull(findContext("Nat::zero"));
    assertNotNull(findContext("Vec::vnil"));
    assertNotNull(findContext("Vec:::>"));
  }

  @Test public void issue382() {
    // success cases, we can find the definition in the context
    compile("data Nat | zero | suc Nat");
    var nat = findContext("Nat");
    assertNotNull(nat);

    // failure cases, the context is unchanged
    assertThrows(Throwable.class, () -> compile("data Nat ="));
    var newNat = findContext("Nat");
    assertEquals(nat, newNat);

    assertThrows(Throwable.class, () -> compile("def a a"));
    assertNull(findContext("a"));
  }

  private @Nullable AnyVar findContext(@NotNull String name) {
    try {
      var ctx = compiler.getContext();
      return ctx.getMaybe(new QualifiedID(SourcePos.NONE,
        ImmutableSeq.of(Constants.SCOPE_SEPARATOR_PATTERN.split(name))));
    } catch (Context.ResolvingInterruptedException ignored) {
      return null;
    }
  }

  private void compile(@NotNull String code) {
    compiler.compileToContext(code, NormalizeMode.NF);
  }
}

// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.cli;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.interactive.ReplCompiler;
import org.aya.generic.Constants;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.resolve.context.Context;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.literate.CodeOptions.NormalizeMode;
import org.aya.syntax.ref.AnyVar;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.ThrowingReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class ReplCompilerTest {
  public final @NotNull ReplCompiler compiler = new ReplCompiler(ImmutableSeq.empty(), new ThrowingReporter(AyaPrettierOptions.debug()), null);

  @BeforeEach public void setup() { compiler.getContext().clear(); }

  @Test public void library() throws IOException {
    compiler.loadToContext(Paths.get("../ide-lsp", "src", "test", "resources", "lsp-test-lib"));
    assertNotNull(findContext("NatCore::zero"));
    assertNotNull(findContext("Vec::vnil"));
    assertNotNull(findContext("Vec:::>"));
  }

  @Test public void simpleExpr() { compile("Set"); }

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

  // /** <a href="https://ice1000.jetbrains.space/im/group/4DLh053zIix6?message=2db0002db&channel=4DLh053zIix6">Bug report</a> */
  // @Test public void reportedInSpace() {
  //   // success cases, we can find the definition in the context
  //   compile("data Unit | unit");
  //   assertNotNull(findContext("Unit"));
  //   compile("data What | what : Unit");
  //   assertNotNull(findContext("What"));
  //   assertNotNull(findContext("Unit"));
  // }

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
    compiler.compileToContext(code, NormalizeMode.FULL);
  }
}

// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.cli;

import java.io.IOException;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.interactive.ReplCompiler;
import org.aya.generic.Constants;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.resolve.context.Context;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.core.term.DepTypeTerm;
import org.aya.syntax.core.term.SortTerm;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.literate.CodeOptions.NormalizeMode;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.CompiledVar;
import org.aya.syntax.ref.DefVar;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.ThrowingReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReplCompilerTest {
  public final @NotNull ReplCompiler compiler = new ReplCompiler(ImmutableSeq.empty(), new ThrowingReporter(AyaPrettierOptions.debug()), null);

  @BeforeEach public void setup() {
    compiler.getContext().clear();
    compiler.imports.clear();
  }

  @Test public void library() throws IOException {
    compiler.loadToContext(Paths.get("../ide-lsp", "src", "test", "resources", "lsp-test-lib"));
    assertNotNull(findContext("VecCore:::>"));
    assertNotNull(findContext("VecCore::vnil"));
    var zero = assertInstanceOf(CompiledVar.class, findContext("Nat::Core::zero"));
    assertNotNull(zero);
    assertEquals("| /* compiled pattern */ ⇒ zero",
      zero.core().easyToString());

    var Nat = assertInstanceOf(CompiledVar.class, findContext("Nat::Core::Nat"));
    assertNotNull(Nat);
    System.out.println(Nat.core().easyToString());

    // Don't be too harsh on the test lib structure, maybe we will change it
    var rootHints = compiler.getContext().giveMeHint(ImmutableSeq.empty());
    assertTrue(rootHints.contains("Nat"));
    assertTrue(rootHints.contains("Path"));

    assertEquals(ImmutableSeq.of("Core"),
      compiler.getContext().giveMeHint(ImmutableSeq.of("Nat")));
    assertEquals(ImmutableSeq.of("Nat", "suc", "zero"),
      compiler.getContext().giveMeHint(ImmutableSeq.of("Nat", "Core")));

    assertTrue(compiler.imports.anyMatch(ii -> ii.modulePath().toString().equals("Nat::Core")));

    // The code below tests that the shape is correctly loaded, for #1174
    compile("open Nat::Core");
    assertInstanceOf(DataCall.class, compiler.computeType("114514", NormalizeMode.NULL));
  }

  @Test public void simpleExpr() { compile("Set"); }

  @Test public void implicitParams() {
    compile("def f {A : Set} (a : A) : A => a");
    var computedType = compiler.computeType("f", NormalizeMode.NULL);
    assertNotNull(computedType);
    var pi = assertInstanceOf(DepTypeTerm.class, computedType);
    assertInstanceOf(SortTerm.class, pi.param());
  }

  @Test public void issue382() {
    // success cases, we can find the definition in the context
    compile("inductive Nat | zero | suc Nat");
    var nat = findContext("Nat");
    assertNotNull(nat);

    // failure cases, the context is unchanged
    assertThrows(Throwable.class, () -> compile("inductive Nat ="));
    var newNat = findContext("Nat");
    assertEquals(nat, newNat);

    assertThrows(Throwable.class, () -> compile("def a a"));
    assertNull(findContext("a"));
  }

  @Test public void info() {
    compile("inductive Nat | zero | suc Nat");
    assertInstanceOf(DefVar.class, compiler.parseToAnyVar("Nat"));
    assertInstanceOf(DefVar.class, compiler.parseToAnyVar("Nat::suc"));
  }

  /** <a href="https://ice1000.jetbrains.space/im/group/4DLh053zIix6?message=2db0002db&channel=4DLh053zIix6">Bug report</a> */
  @Test public void reportedInSpace() {
    // success cases, we can find the definition in the context
    compile("inductive Unit | unit");
    assertNotNull(findContext("Unit"));
    compile("inductive What | what");
    assertNotNull(findContext("What"));
    assertNotNull(findContext("Unit"));
  }

  @Test public void issue1143() {
    compile("module A { module B {} }");
  }

  @Test public void issue1173() {
    compile("prim I prim coe");
    compile("open inductive Nat | zero | suc Nat");
    compile("def infix + (a b : Nat) : Nat elim a | 0 => b | suc n => suc (n + b)");
    // Must be in WHNF mode
    var term = compiler.compileToContext("coe 0 1 (fn x => Nat -> Nat) (+ 3) 3", NormalizeMode.HEAD).getRightValue();
    var integer = assertInstanceOf(IntegerTerm.class, term);
    assertEquals(6, integer.repr());
  }

  private @Nullable AnyVar findContext(@NotNull String name) {
    try {
      var ctx = compiler.getContext();
      return ctx.getMaybe(new QualifiedID(SourcePos.NONE,
        ImmutableSeq.of(Constants.SCOPE_SEPARATOR_PATTERN.split(name))));
    } catch (Context.ResolvingInterruptedException _) {
      return null;
    }
  }

  private void compile(@NotNull String code) {
    compiler.compileToContext(code, NormalizeMode.FULL);
  }
}

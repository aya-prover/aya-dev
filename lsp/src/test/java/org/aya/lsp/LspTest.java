// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.term.DataCall;
import org.aya.core.term.MetaPatTerm;
import org.aya.generic.Constants;
import org.aya.lsp.tester.LspTestClient;
import org.aya.lsp.tester.LspTestCompilerAdvisor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.aya.lsp.tester.TestCommand.compile;
import static org.aya.lsp.tester.TestCommand.mutate;
import static org.junit.jupiter.api.Assertions.*;

public class LspTest {
  public static final @NotNull Path TEST_LIB = Path.of("src", "test", "resources", "lsp-test-lib");

  public @NotNull LspTestClient launch(@NotNull Path libraryRoot) {
    var client = new LspTestClient();
    client.registerLibrary(libraryRoot);
    return client;
  }

  @Test public void testJustLoad() {
    launch(TEST_LIB).execute(compile((a, e) -> {}));
  }

  @Test public void testIncremental() {
    launch(TEST_LIB).execute(
      compile((a, e) -> {}),
      mutate("StringPrims"),
      compile((a, e) -> assertRemake(a, e, "StringPrims", "HelloWorld"))
    );
  }

  @Test public void test541() {
    launch(TEST_LIB).execute(compile((a, e) -> {
      var testOpt = a.lastCompiled()
        .filter(x -> x.moduleName().last().equals("Vec"))
        .flatMap(x -> x.program().get())
        .filterIsInstance(TeleDecl.FnDecl.class)
        .filter(x -> x.ref.name().equals("test"))
        .firstOption();
      assertFalse(testOpt.isEmpty(), "Do not delete the function called test in Vec");
      var testClause = testOpt.get().body.getRightValue().first();
      // vnil, ys => 0
      var testPat = (Pattern.Bind) testClause.patterns.last().term();
      var testTy = assertInstanceOf(DataCall.class, testPat.type().get());
      assertNotNull(testTy);
      // ys : Vec A m
      var lastArg = testTy.args().last().term();
      assertFalse(lastArg instanceof MetaPatTerm);
    }));
  }

  @Test public void testRealWorldLike() {
    launch(TEST_LIB).execute(
      compile((a, e) -> {}),
      mutate("HelloWorld"),
      compile((a, e) -> assertRemake(a, e, "HelloWorld")),
      mutate("Nat"),
      compile((a, e) -> assertRemake(a, e, "Nat", "Vec", "HelloWorld")),
      mutate("PathPrims"),
      compile((a, e) -> assertRemake(a, e, "PathPrims", "Path", "HelloWorld"))
    );
  }

  private void logTime(long time) {
    System.out.println("Remake changed modules took: " + time + "ms");
  }

  private void assertRemake(@NotNull LspTestCompilerAdvisor advisor, long time, @NotNull String... modules) {
    logTime(time);
    assertNotNull(advisor.lastJob);
    var actualInDep = advisor.newlyCompiled.view()
      .map(r -> r.thisModule().moduleName().joinToString(Constants.SCOPE_SEPARATOR))
      .toImmutableSeq();
    var actual = advisor.lastCompiled()
      .map(s -> s.moduleName().joinToString(Constants.SCOPE_SEPARATOR))
      .concat(actualInDep)
      .distinct()
      .toImmutableSeq();
    var expected = ImmutableSeq.from(modules);
    assertEquals(expected.sorted(), actual.sorted());
  }
}

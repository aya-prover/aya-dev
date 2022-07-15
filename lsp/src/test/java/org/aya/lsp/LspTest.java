// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.Constants;
import org.aya.lsp.tester.LspTestClient;
import org.aya.lsp.tester.LspTestCompilerAdvisor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.aya.lsp.tester.TestCommand.compile;
import static org.aya.lsp.tester.TestCommand.mutate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
      compile((a, e) -> assertRemake(a, "StringPrims", "HelloWorld"))
    );
  }

  @Test public void testRealWorldLike() {
    launch(TEST_LIB).execute(
      compile((a, e) -> {}),
      mutate("HelloWorld"),
      compile((a, e) -> assertRemake(a, "HelloWorld")),
      mutate("Nat"),
      compile((a, e) -> assertRemake(a, "Nat", "HelloWorld")),
      mutate("PathPrims"),
      compile((a, e) -> assertRemake(a, "PathPrims", "Path", "HelloWorld"))
    );
  }

  private void assertRemake(@NotNull LspTestCompilerAdvisor advisor, @NotNull String... modules) {
    assertNotNull(advisor.lastJob);
    var actualInDep = advisor.newlyCompiled.view()
      .map(r -> r.thisModule().moduleName().joinToString(Constants.SCOPE_SEPARATOR))
      .toImmutableSeq();
    var actual = advisor.lastJob.view().flatMap(t -> t)
      .map(s -> s.moduleName().joinToString(Constants.SCOPE_SEPARATOR))
      .concat(actualInDep)
      .stream().distinct()
      .collect(ImmutableSeq.factory());
    var expected = ImmutableSeq.from(modules);
    assertEquals(expected.sorted(), actual.sorted());
  }
}

// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.cli.library.incremental.InMemoryCompilerAdvisor;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.source.DiskLibraryOwner;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.core.def.PrimDef;
import org.aya.lsp.prim.LspPrimFactory;
import org.aya.util.FileUtil;
import org.aya.util.reporter.ThrowingReporter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LibraryTest {
  @Test public void testOnDisk() throws IOException {
    FileUtil.deleteRecursively(DIR.resolve("build"));
    // Full rebuild
    assertEquals(0, compile());
    FileUtil.deleteRecursively(DIR.resolve("build").resolve("out"));
    // The second time should load the cache of 'common'.
    assertEquals(0, compile());
    // The third time should do nothing.
    assertEquals(0, compile());
  }

  @Test public void testInMemoryAndPrim() throws IOException {
    var factory = new LspPrimFactory();
    var advisor = new TestAdvisor();
    var owner = DiskLibraryOwner.from(LibraryConfigData.fromLibraryRoot(DIR));
    // Full rebuild
    assertEquals(0, compile(factory, advisor, owner));
    // The second time should load the all sources related to Primitives.aya
    advisor.clearPrimitiveAya();
    assertEquals(0, compile(factory, advisor, owner));
    // The third time should do nothing.
    assertEquals(0, compile(factory, advisor, owner));
  }

  private static final class TestAdvisor extends InMemoryCompilerAdvisor {
    public void clearPrimitiveAya() {
      coreTimestamp.replaceAll((path, time) ->
        path.toString().contains("Primitives.aya") ? FileTime.fromMillis(0) : time);
    }
  }

  public static final Path DIR = TestRunner.DEFAULT_TEST_DIR.resolve("success");

  private static int compile() throws IOException {
    return LibraryCompiler.compile(new PrimDef.Factory(), ThrowingReporter.INSTANCE, TestRunner.flags(), CompilerAdvisor.onDisk(), DIR);
  }

  private static int compile(@NotNull PrimDef.Factory factory, @NotNull CompilerAdvisor advisor, @NotNull LibraryOwner owner) throws IOException {
    return LibraryCompiler.newCompiler(factory, ThrowingReporter.INSTANCE, TestRunner.flags(), advisor, owner).start();
  }
}

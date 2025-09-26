// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSet;
import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.cli.library.incremental.DelegateCompilerAdvisor;
import org.aya.cli.library.incremental.InMemoryCompilerAdvisor;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.source.DiskLibraryOwner;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.utils.CliEnums;
import org.aya.ide.LspPrimFactory;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.primitive.PrimFactory;
import org.aya.util.FileUtil;
import org.aya.util.reporter.ThrowingReporter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LibraryTest testing the compilation of a library and its dependencies
 *
 * @see #testOnDisk
 * @see #testLiterate
 * @see #testInMemoryAndPrim
 */
public class LibraryTest {
  public static final ThrowingReporter REPORTER = new ThrowingReporter(AyaPrettierOptions.pretty());
  @ParameterizedTest
  @ValueSource(strings = {"success"})
  public void testOnDisk(@NotNull String libName) throws IOException {
    var libRoot = TestRunner.TEST_DIR.resolve(libName);

    FileUtil.deleteRecursively(libRoot.resolve("build"));
    // Full rebuild
    assertEquals(0, compile(libRoot));
    // The second time should load the cache of 'common'.
    FileUtil.deleteRecursively(libRoot.resolve("build").resolve("out"));
    assertEquals(0, compile(libRoot));
    // The third time should do nothing.
    assertEquals(0, compile(libRoot));
  }

  // Use this test for incremental compilation
  public static void main(String... args) throws IOException {
    assertEquals(0, compile(DIR));
  }

  @Test public void testLiterate() throws IOException {
    FileUtil.deleteRecursively(DIR.resolve("build"));
    // Full rebuild
    assertEquals(0, compile(makeFlagsForPretty(), DIR));
  }

  private static @NotNull CompilerFlags makeFlagsForPretty() {
    var prettyInfo = new CompilerFlags.PrettyInfo(
      true, false, false, false, CliEnums.PrettyStage.literate,
      CliEnums.PrettyFormat.html, AyaPrettierOptions.pretty(), new RenderOptions(), null, null, null
    );
    return new CompilerFlags(CompilerFlags.Message.ASCII, false, false, prettyInfo, SeqView.empty(), null);
  }

  @Test public void testInMemoryAndPrim() throws IOException {
    var factory = new LspPrimFactory();
    var advisor = new TestAdvisor();

    // Test delegate advisor delegates all methods to real advisor.
    var data = new InterceptData();
    var intercept = DelegateIntercept.fromRealAdvisor(advisor, data);
    var delegated = new DelegateCompilerAdvisor(intercept);

    var owner = DiskLibraryOwner.from(LibraryConfigData.fromLibraryRoot(DIR));
    // Full rebuild
    assertEquals(0, compile(factory, delegated, owner));
    // The second time should load the all sources related to Primitives.aya
    advisor.clearPrimitiveAya();
    assertEquals(0, compile(factory, delegated, owner));
    // The third time should do nothing.
    assertEquals(0, compile(factory, delegated, owner));

    // Test delegate advisor delegates all methods to real advisor.
    data.assertDelegate();
  }

  private static final class TestAdvisor extends InMemoryCompilerAdvisor {
    public void clearPrimitiveAya() {
      coreTimestamp.replaceAll((path, time) ->
        path.toString().contains("Primitives.aya") ? FileTime.fromMillis(0) : time);
    }
  }

  private record DelegateIntercept(
    @NotNull CompilerAdvisor realAdvisor,
    @NotNull InterceptData data
  ) implements InvocationHandler {
    public static @NotNull CompilerAdvisor
    fromRealAdvisor(@NotNull CompilerAdvisor realAdvisor, @NotNull InterceptData data) {
      return (CompilerAdvisor) Proxy.newProxyInstance(
        LibraryTest.class.getClassLoader(),
        new Class[]{CompilerAdvisor.class},
        new DelegateIntercept(realAdvisor, data)
      );
    }

    @Override public Object invoke(Object proxy, @NotNull Method method, Object[] args) throws Throwable {
      data.methodsCalled.add(method.getName());
      return method.invoke(realAdvisor, args);
    }
  }

  private record InterceptData(@NotNull MutableSet<String> methodsCalled) {
    public InterceptData() {
      this(MutableSet.create());
    }

    public void assertDelegate() {
      var notCalled = ImmutableSeq.from(CompilerAdvisor.class.getMethods())
        .view()
        .filter(m -> (m.getModifiers() & Modifier.STATIC) == 0)
        .filterNot(Method::isDefault)
        .map(Method::getName)
        .filterNot(methodsCalled::contains)
        .toSeq();
      assertEquals(ImmutableSeq.empty(), notCalled);
    }
  }

  public static final Path DIR = TestRunner.TEST_DIR.resolve("success");

  private static int compile(@NotNull Path root) throws IOException {
    return compile(TestRunner.flags(), root);
  }

  private static int compile(@NotNull CompilerFlags flags, @NotNull Path root) throws IOException {
    return LibraryCompiler.compile(new PrimFactory(), REPORTER, flags, CompilerAdvisor.onDisk(), root);
  }

  private static int compile(@NotNull PrimFactory factory, @NotNull CompilerAdvisor advisor, @NotNull LibraryOwner owner) throws IOException {
    return LibraryCompiler.newCompiler(factory, REPORTER, TestRunner.flags(), advisor, owner).start();
  }
}

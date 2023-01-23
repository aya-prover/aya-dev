// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSet;
import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.cli.library.incremental.DelegateCompilerAdvisor;
import org.aya.cli.library.incremental.InMemoryCompilerAdvisor;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.source.DiskLibraryOwner;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.core.def.PrimDef;
import org.aya.ide.LspPrimFactory;
import org.aya.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LibraryTest {
  @Test public void testOnDisk() throws IOException {
    FileUtil.deleteRecursively(DIR.resolve("build"));
    // Full rebuild
    assertEquals(0, compile());
    // The second time should load the cache of 'common'.
    FileUtil.deleteRecursively(DIR.resolve("build").resolve("out"));
    assertEquals(0, compile());
    // The third time should do nothing.
    assertEquals(0, compile());
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
    public static @NotNull CompilerAdvisor fromRealAdvisor(@NotNull CompilerAdvisor realAdvisor, @NotNull InterceptData data) {
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
        .toImmutableSeq();
      assertEquals(ImmutableSeq.empty(), notCalled);
    }
  }

  public static final Path DIR = TestRunner.DEFAULT_TEST_DIR.resolve("success");

  private static int compile() throws IOException {
    return LibraryCompiler.compile(new PrimDef.Factory(), AyaThrowingReporter.INSTANCE, TestRunner.flags(), CompilerAdvisor.onDisk(), DIR);
  }

  private static int compile(@NotNull PrimDef.Factory factory, @NotNull CompilerAdvisor advisor, @NotNull LibraryOwner owner) throws IOException {
    return LibraryCompiler.newCompiler(factory, AyaThrowingReporter.INSTANCE, TestRunner.flags(), advisor, owner).start();
  }
}

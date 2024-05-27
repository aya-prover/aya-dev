// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.cli;

import com.javax0.sourcebuddy.Compiler;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

public class SimpleTest {
  public static void foo() {
    System.out.println("Hello, world!");
  }

  @Test public void test() {
    Class<?> foo;
    try {
      @Language("Java") var code = """
        package org.aya.gen;

        import org.aya.test.cli.SimpleTest;

        public class Foo {
          public static void foo() {
            SimpleTest.foo();
          }
        }
        """;

      foo = Compiler.java().from(code).compile().load().get();
      var methodFoo = foo.getDeclaredMethod("foo");
      methodFoo.invoke(null);
    } catch (Compiler.CompileException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
             ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}

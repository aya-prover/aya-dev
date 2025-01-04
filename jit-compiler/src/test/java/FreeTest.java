// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import org.aya.compiler.free.morphism.free.FreeJavaBuilderImpl;
import org.aya.compiler.free.morphism.free.FreeRunner;
import org.aya.compiler.free.morphism.source.SourceFreeJavaBuilder;
import org.aya.compiler.serializers.ModuleSerializer;
import org.aya.resolve.module.DumbModuleLoader;
import org.aya.syntax.core.def.TopLevelDef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

public class FreeTest {
  public static ModuleSerializer serializer = null;
  public static ModuleSerializer.ModuleResult moduleResult = null;

  @BeforeAll
  public static void init() {
    var result = CompileTest.tyck("""
      open inductive Nat | zero | suc Nat
      def plus Nat Nat : Nat
      | 0, b => b
      | suc a, b => suc (plus a b)
      """);

    serializer = new ModuleSerializer(result.info().shapeFactory());
    moduleResult = new ModuleSerializer.ModuleResult(
      DumbModuleLoader.DUMB_MODULE_NAME, result.defs().filterIsInstance(TopLevelDef.class)
    );
  }

  @Test public void free2source() throws IOException {
    var free = serializer.serialize(FreeJavaBuilderImpl.INSTANCE, moduleResult);
    var result = new FreeRunner<>(SourceFreeJavaBuilder.create())
      .runFree(free);

    Files.writeString(CompileTester.GEN_DIR.resolve("freeJavaBaka.java"), result);
  }
}

// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.jit;

import org.aya.compiler.ModuleSerializer;
import org.aya.compiler.SourceBuilder;
import org.aya.compiler.free.morphism.SourceFreeJavaBuilder;
import org.aya.syntax.core.def.TopLevelDef;
import org.aya.tyck.TyckTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JitTest {
  public static final @NotNull Path GEN_DIR = Path.of("src", "test", "resources", "gen");

  @Test
  public void test0() throws IOException {
    var result = TyckTest.tyck("""
      open inductive Nat | O | S Nat
      
      def what Nat Nat : Nat
      | 0, 0 => 0
      | 0, b => b
      | S a, b => S (what a b)
      """);

    var topDefs = result.defs()
      .filterIsInstance(TopLevelDef.class);

    var modResult = new ModuleSerializer.ModuleResult(topDefs.get(0).ref().module, topDefs);
    var output = new ModuleSerializer<String>(result.info().shapeFactory())
      .serialize(new SourceFreeJavaBuilder(new SourceBuilder.Default()), modResult);
    Files.write(GEN_DIR.resolve("114514.java"), output.getBytes());
  }
}

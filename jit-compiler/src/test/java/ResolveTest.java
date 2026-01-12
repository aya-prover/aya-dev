// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import org.aya.compiler.CompiledModule;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.context.PhysicalModuleContext;
import org.aya.resolve.module.DumbModuleLoader;
import org.aya.states.primitive.PrimFactory;
import org.aya.util.FileUtil;
import org.aya.util.reporter.ThrowingReporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class ResolveTest {
  @Test
  public void test() throws IOException {
    var result = CompileTest.tyck("""
          open inductive Unit | tt
          private def privateDef : Unit => tt
          def publicDef : Unit => tt
      
          module Sub {
              private def privateDefInSub : Unit => tt
              def publicDefInSub : Unit => tt
          }
      
          module Sub2 {
              def publicDefInSub2 : Unit => tt
          }
      
          module Sub3 {
              def infixr publicDefInSub3 Unit Unit : Unit => tt
          }
      
          module Sub4 {
              open Sub3
              def publicDefInSub4 : Unit => tt
          }
      
          module Sub5 {
              def publicDefInSub5 Unit Unit : Unit => tt
          }
      
          open Sub2
          open Sub5 using (publicDefInSub5 as infixl *)
      """);

    var info = result.info();
    var compiledModule = CompiledModule.from(info, result.defs());

    var base = CompileTest.GEN_DIR.resolve("resolveTest");

    FileUtil.deleteRecursively(base);
    CompileTest.serializeFrom(result, base);

    try (var innerLoader = new URLClassLoader(new URL[]{base.toUri().toURL()}, getClass().getClassLoader())) {
      var reporter = new ThrowingReporter(AyaPrettierOptions.debug());
      var baseCtx = (PhysicalModuleContext) new EmptyContext(Path.of("baka"))
        .derive(DumbModuleLoader.DUMB_MODULE_STRING);
      var deser = compiledModule.toResolveInfo(
        new DumbModuleLoader(new ThrowingReporter(AyaPrettierOptions.debug()), baseCtx),
        baseCtx, innerLoader, new PrimFactory(), reporter
      );

      return;
    }
  }
}

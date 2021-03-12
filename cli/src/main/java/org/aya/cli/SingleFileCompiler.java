// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import org.aya.api.error.CountingReporter;
import org.aya.api.error.Reporter;
import org.aya.api.util.InterruptException;
import org.aya.concrete.Signatured;
import org.aya.concrete.Stmt;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.parse.AyaProducer;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.EmptyModuleLoader;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.resolve.module.ModuleListLoader;
import org.aya.concrete.resolve.visitor.StmtShallowResolver;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public record SingleFileCompiler(@NotNull Reporter reporter, @NotNull Path filePath, Trace.@Nullable Builder builder) {
  public int compile(@NotNull CompilerFlags flags) throws IOException {
    var reporter = new CountingReporter(this.reporter);
    var parser = AyaParsing.parser(filePath, reporter);
    try {
      var program = new AyaProducer(reporter).visitProgram(parser.program());
      var context = new EmptyContext(reporter).derive();
      var loader = new ModuleListLoader(flags.modulePaths().map(path ->
        new CachedModuleLoader(new FileModuleLoader(path, reporter, builder))));
      var shallowResolver = new StmtShallowResolver(loader);
      program.forEach(s -> s.accept(shallowResolver, context));
      program.forEach(Stmt::resolve);
      program.forEach(s -> {
        if (s instanceof Signatured decl) decl.tyck(reporter, builder);
      });
    } catch (ExprTycker.TyckerException | Context.ContextException e) {
      e.printStackTrace();
      e.printHint();
      System.err.println("""
        Please report the stacktrace to the developers so a better error handling could be made.
        Don't forget to inform the version of Aya you're using and attach your code for reproduction.""");
      return e.exitCode();
    } catch (InterruptException e) {
      // TODO[ice]: proper error handling
      reporter.reportString(e.stage().name() + " interrupted due to errors.");
      if (flags.interruptedTrace()) e.printStackTrace();
    }
    if (reporter.isEmpty()) {
      reporter.reportString(flags.message().successNotion());
      return 0;
    } else {
      reporter.reportString(flags.message().failNotion());
      return -1;
    }
  }
}

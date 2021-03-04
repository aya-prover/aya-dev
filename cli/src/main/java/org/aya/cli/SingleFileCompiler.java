// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import org.aya.api.error.Reporter;
import org.aya.api.util.MziInterruptException;
import org.aya.concrete.Signatured;
import org.aya.concrete.Stmt;
import org.aya.concrete.parse.MziParsing;
import org.aya.concrete.parse.MziProducer;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.module.EmptyModuleLoader;
import org.aya.concrete.resolve.visitor.StmtShallowResolver;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.CountingReporter;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public record SingleFileCompiler(@NotNull Reporter reporter, @NotNull Path filePath, Trace.@Nullable Builder builder) {
  public int compile(@NotNull CompilerFlags flags) throws IOException {
    var reporter = new CountingReporter(this.reporter);
    var parser = MziParsing.parser(filePath, reporter);
    try {
      var program = MziProducer.INSTANCE.visitProgram(parser.program());
      var context = new EmptyContext(reporter).derive();
      var shallowResolver = new StmtShallowResolver(new EmptyModuleLoader());
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
        Don't forget to inform the version of Mzi you're using and attach your code for reproduction.""");
      return e.exitCode();
    } catch (MziInterruptException e) {
      // TODO[ice]: proper error handling
      reporter.reportString(e.stage().name() + " interrupted due to errors.");
    }
    if (reporter.isEmpty()) {
      reporter.reportString(flags.successNotion());
      return 0;
    } else {
      reporter.reportString(flags.failNotion());
      return -1;
    }
  }
}

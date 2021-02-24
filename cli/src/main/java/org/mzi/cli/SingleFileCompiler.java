// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.cli;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Reporter;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Stmt;
import org.mzi.concrete.parse.MziParsing;
import org.mzi.concrete.parse.MziProducer;
import org.mzi.concrete.resolve.context.Context;
import org.mzi.concrete.resolve.context.EmptyContext;
import org.mzi.concrete.resolve.module.EmptyModuleLoader;
import org.mzi.concrete.resolve.visitor.StmtShallowResolver;
import org.mzi.tyck.ExprTycker;
import org.mzi.tyck.error.CountingReporter;

import java.io.IOException;
import java.nio.file.Path;

public record SingleFileCompiler(@NotNull Reporter reporter, @NotNull Path filePath) {
  public int compile(@NotNull CompilerFlags flags) throws IOException {
    var reporter = new CountingReporter(this.reporter);
    var parser = MziParsing.parser(filePath, reporter);
    var program = MziProducer.INSTANCE.visitProgram(parser.program());
    var context = new EmptyContext(reporter).derive();
    var shallowResolver = new StmtShallowResolver(new EmptyModuleLoader());
    try {
      program.forEach(s -> s.accept(shallowResolver, context));
      program.forEach(Stmt::resolve);
      program.forEach(s -> {
        if (s instanceof Decl decl) decl.tyck(reporter);
      });
    } catch (ExprTycker.TyckerException | Context.ContextException e) {
      e.printStackTrace();
      e.printHint();
      System.err.println("""
        Please report the stacktrace to the developers so a better error handling could be made.
        Don't forget to inform the version of Mzi you're using and attach your code for reproduction.""");
      return e.exitCode();
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

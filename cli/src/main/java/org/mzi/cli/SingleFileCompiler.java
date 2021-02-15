// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.cli;

import org.jetbrains.annotations.NotNull;
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

public class SingleFileCompiler {
  public static @NotNull String TQL = "\uD83D\uDC02\uD83C\uDF7A";
  public static @NotNull String NMSL = "\uD83D\uDD28";

  public static void compile(@NotNull Path filePath) throws IOException {
    var reporter = new CountingReporter(new CliReporter(filePath));
    var parser = MziParsing.parser(filePath, reporter);
    var program = MziProducer.INSTANCE.visitProgram(parser.program());
    var context = new EmptyContext(reporter).derive();
    var shallowResolver = new StmtShallowResolver(new EmptyModuleLoader());
    try {
      program.forEach(s -> {
        s.desugar();
        s.accept(shallowResolver, context);
      });
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
    }
    if (reporter.isEmpty()) System.out.println(TQL);
    else System.err.println(NMSL);
  }
}

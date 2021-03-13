// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.module;

import org.aya.api.error.Reporter;
import org.aya.api.ref.Var;
import org.aya.api.util.InterruptException;
import org.aya.concrete.Signatured;
import org.aya.concrete.Stmt;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.parse.AyaProducer;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.context.ModuleContext;
import org.aya.concrete.resolve.visitor.StmtShallowResolver;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.trace.Trace;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public final record FileModuleLoader(
  @NotNull Path basePath,
  @NotNull Reporter reporter,
  Trace.@Nullable Builder builder
) implements ModuleLoader {
  @Override
  public @Nullable MutableMap<Seq<String>, MutableMap<String, Var>>
  load(@NotNull Seq<@NotNull String> path, @NotNull ModuleLoader recurseLoader) {
    try {
      var parser = AyaParsing.parser(path.foldLeft(basePath, Path::resolve), reporter());
      var program = new AyaProducer(reporter).visitProgram(parser.program());
      return tyckModule(recurseLoader, program, reporter, builder).exports();
    } catch (IOException e) {
      reporter.reportString(e.getMessage());
      return null;
    } catch (ExprTycker.TyckerException | Context.ContextException e) {
      return null;
    } catch (InterruptException e) {
      // TODO[ice]: proper error handling
      reporter.reportString(e.stage().name() + " interrupted due to errors.");
      return null;
    }

  }

  public static @NotNull ModuleContext tyckModule(
    @NotNull ModuleLoader recurseLoader,
    @NotNull ImmutableSeq<Stmt> program,
    @NotNull Reporter reporter,
    Trace.@Nullable Builder builder
  ) {
    var context = new EmptyContext(reporter).derive();
    var shallowResolver = new StmtShallowResolver(recurseLoader);
    program.forEach(s -> s.accept(shallowResolver, context));
    program.forEach(Stmt::resolve);
    program.forEach(s -> {
      if (s instanceof Signatured decl) decl.tyck(reporter, builder);
    });
    return context;
  }
}

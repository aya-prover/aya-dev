// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.module;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableMap;
import kala.function.CheckedConsumer;
import kala.function.CheckedRunnable;
import org.aya.api.error.DelayedReporter;
import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.ref.Var;
import org.aya.api.util.InternalException;
import org.aya.api.util.InterruptException;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.context.PhysicalModuleContext;
import org.aya.concrete.resolve.visitor.StmtShallowResolver;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Sample;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.Def;
import org.aya.tyck.ExprTycker;
import org.aya.core.def.PrimDef;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public record FileModuleLoader(
  @NotNull SourceFileLocator locator,
  @NotNull Path basePath,
  @NotNull Reporter reporter,
  Trace.@Nullable Builder builder
) implements ModuleLoader {
  private @NotNull Path resolveFile(@NotNull Seq<@NotNull String> path) {
    var withoutExt = path.foldLeft(basePath, Path::resolve);
    return withoutExt.resolveSibling(withoutExt.getFileName() + ".aya");
  }

  @Override public @Nullable MutableMap<Seq<String>, MutableMap<String, Var>>
  load(@NotNull Seq<@NotNull String> path, @NotNull ModuleLoader recurseLoader) {
    var sourcePath = resolveFile(path);
    var primFactory = PrimDef.PrimFactory.create();
    try {
      var program = AyaParsing.program(locator, reporter, sourcePath, primFactory);
        return tyckModule(recurseLoader, program, reporter, () -> {}, defs -> {}, builder, primFactory).exports;
    } catch (IOException e) {
      reporter.reportString(e.getMessage());
      return null;
    } catch (InternalException e) {
      handleInternalError(e);
      return null;
    } catch (InterruptException e) {
      reporter.reportString(e.stage().name() + " interrupted due to error(s).");
      return null;
    }
  }

  public static <E extends Exception> @NotNull PhysicalModuleContext tyckModule(
    @NotNull ModuleLoader recurseLoader,
    @NotNull ImmutableSeq<Stmt> program,
    @NotNull Reporter reporter,
    @NotNull CheckedRunnable<E> onResolved,
    @NotNull CheckedConsumer<ImmutableSeq<Def>, E> onTycked,
    Trace.@Nullable Builder builder,
    PrimDef.PrimFactory primFactory
  ) throws E {
    var context = new EmptyContext(reporter).derive();
    var shallowResolver = new StmtShallowResolver(recurseLoader);
    program.forEach(s -> s.accept(shallowResolver, context));
    var opSet = new BinOpSet(reporter);
    program.forEach(s -> s.resolve(opSet));
    opSet.sort();
    program.forEach(s -> s.desugar(reporter, opSet));
    // in case we have un-messaged TyckException
    try (var delayedReporter = new DelayedReporter(reporter)) {
      var wellTyped = Buffer.<Def>create();
      for (var stmt : program) {
        if (stmt instanceof Decl decl) wellTyped.append(decl.tyck(delayedReporter, builder,primFactory ));
        else if (stmt instanceof Sample sample) wellTyped.append(sample.tyck(delayedReporter, builder, primFactory));
        else if (stmt instanceof Remark remark) {
          var literate = remark.literate;
          if (literate != null) literate.tyck(new ExprTycker(delayedReporter, builder));
        }
        if (delayedReporter.problems().anyMatch(Problem::isError)) break;
      }
      onTycked.acceptChecked(wellTyped.toImmutableSeq());
    } finally {
      onResolved.runChecked();
    }
    return context;
  }

  public static void handleInternalError(@NotNull InternalException e) {
    e.printStackTrace();
    e.printHint();
    System.err.println("""
      Please report the stacktrace to the developers so a better error handling could be made.
      Don't forget to inform the version of Aya you're using and attach your code for reproduction.""");
  }
}

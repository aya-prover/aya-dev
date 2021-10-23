// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.module;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableMap;
import kala.function.CheckedConsumer;
import org.aya.api.error.DelayedReporter;
import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.ref.Var;
import org.aya.api.util.InternalException;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.context.ModuleContext;
import org.aya.concrete.resolve.context.PhysicalModuleContext;
import org.aya.concrete.resolve.visitor.StmtShallowResolver;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Sample;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.Def;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public record FileModuleLoader(
  @NotNull SourceFileLocator locator,
  @NotNull Path basePath,
  @NotNull Reporter reporter,
  @Nullable FileModuleLoaderCallback callback,
  Trace.@Nullable Builder builder
) implements ModuleLoader {
  public interface FileModuleLoaderCallback {
    void onResolved(@NotNull Path sourcePath, @NotNull FileModuleLoader.FileResolveInfo moduleInfo, @NotNull ImmutableSeq<Stmt> stmts);
    void onTycked(@NotNull Path sourcePath,
                  @NotNull ImmutableSeq<Stmt> stmts,
                  @NotNull ImmutableSeq<Def> defs);
  }

  private @NotNull Path resolveFile(@NotNull Seq<@NotNull String> path) {
    var withoutExt = path.foldLeft(basePath, Path::resolve);
    return withoutExt.resolveSibling(withoutExt.getFileName() + ".aya");
  }

  @Override public @Nullable MutableMap<ImmutableSeq<String>, MutableMap<String, Var>>
  load(@NotNull ImmutableSeq<@NotNull String> path, @NotNull ModuleLoader recurseLoader) {
    var sourcePath = resolveFile(path);
    try {
      var program = AyaParsing.program(locator, reporter, sourcePath);
      return tyckModule(path, recurseLoader, program, reporter,
        resolveInfo -> {
          if (callback != null) callback.onResolved(sourcePath, resolveInfo, program);
        },
        defs -> {
          if (callback != null) callback.onTycked(sourcePath, program, defs);
        },
        builder).exports;
    } catch (IOException e) {
      return null;
    }
  }

  public static <E extends Exception> void tyckModule(
    @NotNull ModuleContext context,
    @NotNull ModuleLoader recurseLoader,
    @NotNull ImmutableSeq<Stmt> program,
    @NotNull Reporter reporter,
    @NotNull CheckedConsumer<FileResolveInfo, E> onResolved,
    @NotNull CheckedConsumer<ImmutableSeq<Def>, E> onTycked,
    Trace.@Nullable Builder builder
  ) throws E {
    var resolveInfo = new FileResolveInfo(Buffer.create());
    var shallowResolver = new StmtShallowResolver(recurseLoader, resolveInfo);
    program.forEach(s -> s.accept(shallowResolver, context));
    var opSet = new BinOpSet(reporter);
    program.forEach(s -> s.resolve(opSet));
    opSet.sort();
    program.forEach(s -> s.desugar(reporter, opSet));
    // in case we have un-messaged TyckException
    try (var delayedReporter = new DelayedReporter(reporter)) {
      var wellTyped = Buffer.<Def>create();
      for (var stmt : program) {
        if (stmt instanceof Decl decl) wellTyped.append(decl.tyck(delayedReporter, builder));
        else if (stmt instanceof Sample sample) wellTyped.append(sample.tyck(delayedReporter, builder));
        else if (stmt instanceof Remark remark) {
          var literate = remark.literate;
          if (literate != null) literate.tyck(new ExprTycker(delayedReporter, builder));
        }
        if (delayedReporter.problems().anyMatch(Problem::isError)) break;
      }
      onTycked.acceptChecked(wellTyped.toImmutableSeq());
    } finally {
      onResolved.acceptChecked(resolveInfo);
    }
  }

  public static <E extends Exception> @NotNull PhysicalModuleContext tyckModule(
    @NotNull ImmutableSeq<@NotNull String> path,
    @NotNull ModuleLoader recurseLoader,
    @NotNull ImmutableSeq<Stmt> program,
    @NotNull Reporter reporter,
    @NotNull CheckedConsumer<FileResolveInfo, E> onResolved,
    @NotNull CheckedConsumer<ImmutableSeq<Def>, E> onTycked,
    Trace.@Nullable Builder builder
  ) throws E {
    var context = new EmptyContext(reporter).derive(path);
    tyckModule(context, recurseLoader, program, reporter, onResolved, onTycked, builder);
    return context;
  }

  /**
   * Copied and adapted.
   *
   * @see #tyckModule
   */
  public static ExprTycker.@NotNull Result tyckExpr(
    @NotNull ModuleContext context,
    @NotNull Expr expr,
    @NotNull Reporter reporter,
    Trace.@Nullable Builder builder
  ) {
    var resolvedExpr = expr.resolve(context);
    // in case we have un-messaged TyckException
    try (var delayedReporter = new DelayedReporter(reporter)) {
      return resolvedExpr
        .desugar(delayedReporter)
        .tyck(delayedReporter, builder);
    }
  }

  public static void handleInternalError(@NotNull InternalException e) {
    e.printStackTrace();
    e.printHint();
    System.err.println("""
      Please report the stacktrace to the developers so a better error handling could be made.
      Don't forget to inform the version of Aya you're using and attach your code for reproduction.""");
  }

  public record FileResolveInfo(
    @NotNull Buffer<ImmutableSeq<String>> imports
  ) {
  }
}

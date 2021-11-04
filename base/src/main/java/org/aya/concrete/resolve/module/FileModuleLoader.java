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
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.ShallowResolveInfo;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.context.ModuleContext;
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
    void onResolved(@NotNull Path sourcePath, @NotNull ShallowResolveInfo moduleInfo, @NotNull ImmutableSeq<Stmt> stmts);
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
      var context = new EmptyContext(reporter).derive(path);
      tyckModule(context, recurseLoader, program, reporter,
        resolveInfo -> {
          if (callback != null) callback.onResolved(sourcePath, resolveInfo, program);
        },
        defs -> {
          if (callback != null) callback.onTycked(sourcePath, program, defs);
        },
        builder);
      return context.exports;
    } catch (IOException e) {
      return null;
    }
  }

  public static <E extends Exception> void tyckModule(
    @NotNull ModuleContext context,
    @NotNull ModuleLoader recurseLoader,
    @NotNull ImmutableSeq<Stmt> program,
    @NotNull Reporter reporter,
    @NotNull CheckedConsumer<ShallowResolveInfo, E> onResolved,
    @NotNull CheckedConsumer<ImmutableSeq<Def>, E> onTycked,
    Trace.@Nullable Builder builder
  ) throws E {
    var shallowResolveInfo = new ShallowResolveInfo(Buffer.create());
    var shallowResolver = new StmtShallowResolver(recurseLoader, shallowResolveInfo);
    program.forEach(s -> s.accept(shallowResolver, context));
    var resolveInfo = new ResolveInfo(new BinOpSet(reporter));
    program.forEach(s -> s.resolve(resolveInfo));
    resolveInfo.opSet().reportIfCycles();
    program.forEach(s -> s.desugar(reporter, resolveInfo.opSet()));
    // in case we have un-messaged TyckException
    try (var delayedReporter = new DelayedReporter(reporter)) {
      var SCCs = resolveInfo.declGraph().topologicalOrder()
        .view().appendedAll(resolveInfo.sampleGraph().topologicalOrder());
      // show(SCCs);
      var wellTyped = SCCs
        .flatMap(scc -> tyckSCC(scc, builder, delayedReporter))
        .toImmutableSeq();
      onTycked.acceptChecked(wellTyped);
    } catch (GroupTyckingFailed ignored) {
      // stop tycking the rest of groups since some of their dependencies are failed.
      // Random thought: we may assume their signatures are correct and try to tyck
      // the rest of program as much as possible, which can make LSP more user-friendly?
    } finally {
      onResolved.acceptChecked(shallowResolveInfo);
    }
  }

  // private static void show(SeqView<ImmutableSeq<Stmt>> sccs) {
  //   System.out.println("==== Tyck order ====");
  //   sccs.forEach(scc -> {
  //     System.out.println("  Group:");
  //     scc.forEach(s -> {
  //       switch (s) {
  //         case Decl decl -> System.out.println("    Decl: " + decl.ref().name());
  //         case Sample sam -> System.out.println((sam instanceof Sample.Working ? "    Example: " : "    CounterExample: ") + ((Decl) sam.delegate()).ref().name());
  //         case Remark rem -> System.out.println("    Remark: " + rem);
  //         default -> {
  //         }
  //       }
  //     });
  //   });
  //   System.out.println("====================");
  // }

  /**
   * Tyck a group of statements in an SCC.
   */
  private static @NotNull ImmutableSeq<Def> tyckSCC(@NotNull ImmutableSeq<Stmt> scc, Trace.@Nullable Builder builder, DelayedReporter reporter) {
    var wellTyped = Buffer.<Def>create();
    for (var stmt : scc) {
      if (stmt instanceof Decl decl) wellTyped.append(decl.tyck(reporter, builder));
      else if (stmt instanceof Sample sample) wellTyped.append(sample.tyck(reporter, builder));
      else if (stmt instanceof Remark remark) {
        var literate = remark.literate;
        if (literate != null) literate.tyck(new ExprTycker(reporter, builder));
      }
      if (reporter.problems().anyMatch(Problem::isError)) throw new GroupTyckingFailed();
    }
    return wellTyped.toImmutableSeq();
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
      var tycker = new ExprTycker(delayedReporter, builder);
      return tycker.zonk(expr, tycker.synthesize(resolvedExpr.desugar(delayedReporter)));
    }
  }

  public static void handleInternalError(@NotNull InternalException e) {
    e.printStackTrace();
    e.printHint();
    System.err.println("""
      Please report the stacktrace to the developers so a better error handling could be made.
      Don't forget to inform the version of Aya you're using and attach your code for reproduction.""");
  }

  private static class GroupTyckingFailed extends RuntimeException {
  }
}

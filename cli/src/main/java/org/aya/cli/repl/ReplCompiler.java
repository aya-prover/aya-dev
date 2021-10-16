// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.value.Ref;
import org.aya.api.error.CountingReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.util.InterruptException;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.context.PhysicalModuleContext;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.resolve.module.ModuleListLoader;
import org.aya.core.def.Def;
import org.aya.core.term.Term;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class ReplCompiler {
  @NotNull Reporter reporter;
  @Nullable SourceFileLocator locator;
  Trace.@Nullable Builder builder;
  @NotNull PhysicalModuleContext context;

  public ReplCompiler(@NotNull Reporter reporter, @Nullable SourceFileLocator locator, Trace.@Nullable Builder builder) {
    this.reporter = reporter;
    this.locator = locator;
    this.builder = builder;
    context = new EmptyContext(reporter).derive(Seq.empty());
  }

  /**
   * Copied and adapted.
   *
   * @param text the text of code to compile, witch might either be a `program` or an `expr`.
   * @see org.aya.cli.single.SingleFileCompiler#compile
   */
  @Nullable Either<ImmutableSeq<Def>, Term> compileAndAddToContext(
    @NotNull String text,
    @NotNull SeqLike<Path> modulePaths,
    @Nullable FileModuleLoader.FileModuleLoaderCallback moduleCallback
  ) {
    var reporter = new CountingReporter(this.reporter);
    var locator = this.locator != null ? this.locator : new SourceFileLocator.Module(modulePaths);

    try {
      var programOrExpr = AyaParsing.repl(reporter, text);
      if (programOrExpr == null) return null;

      var loader = new ModuleListLoader(modulePaths.view().map(path ->
        new CachedModuleLoader(new FileModuleLoader(locator, path, reporter, moduleCallback, builder))).toImmutableSeq());

      return programOrExpr.map(
        program -> {
          var newDefs = new Ref<ImmutableSeq<Def>>();
          FileModuleLoader.tyckModule(context, loader, program, reporter,
            resolveInfo -> {},
            newDefs::set, builder);
          return newDefs.get();
        },
        expr -> FileModuleLoader.tyckExpr(context, expr, reporter, builder).wellTyped()
          .normalize(NormalizeMode.NF)
      );
    } catch (InterruptException ignored) {
      return Either.left(ImmutableSeq.empty());
    }
  }
}

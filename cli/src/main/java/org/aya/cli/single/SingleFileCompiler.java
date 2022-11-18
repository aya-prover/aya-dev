// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.cli.utils.AyaCompiler;
import org.aya.cli.utils.MainArgs;
import org.aya.concrete.stmt.Decl;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.serde.Serializer;
import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.ModuleCallback;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.module.CachedModuleLoader;
import org.aya.resolve.module.FileModuleLoader;
import org.aya.resolve.module.ModuleListLoader;
import org.aya.tyck.trace.Trace;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourceFileLocator;
import org.aya.util.reporter.CountingReporter;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Function;

public record SingleFileCompiler(
  @NotNull Reporter reporter,
  @Nullable SourceFileLocator locator,
  @Nullable Trace.Builder builder
) {
  public <E extends IOException> int compile(
    @NotNull Path sourceFile,
    @NotNull CompilerFlags flags,
    @Nullable ModuleCallback<E> moduleCallback
  ) throws IOException {
    return compile(sourceFile, reporter -> new EmptyContext(reporter, sourceFile).derive(ImmutableSeq.of("Mian")), flags, moduleCallback);
  }

  public <E extends IOException> int compile(
    @NotNull Path sourceFile,
    @NotNull Function<Reporter, ModuleContext> context,
    @NotNull CompilerFlags flags,
    @Nullable ModuleCallback<E> moduleCallback
  ) throws IOException {
    var reporter = CountingReporter.of(this.reporter);
    var ctx = context.apply(reporter);
    var locator = this.locator != null ? this.locator : new SourceFileLocator.Module(flags.modulePaths());
    var primFactory = new PrimDef.Factory();
    return AyaCompiler.catching(reporter, flags, () -> {
      var ayaParser = new AyaParserImpl(reporter);
      var program = ayaParser.program(locator, sourceFile);
      var distillInfo = flags.distillInfo();
      distill(sourceFile, distillInfo, program, MainArgs.DistillStage.raw);
      var loader = new CachedModuleLoader<>(new ModuleListLoader(reporter, flags.modulePaths().view().map(path ->
        new FileModuleLoader(locator, path, reporter, ayaParser, primFactory, builder)).toImmutableSeq()));
      loader.tyckModule(primFactory, ctx, program, builder, (moduleResolve, defs) -> {
        distill(sourceFile, distillInfo, program, MainArgs.DistillStage.scoped);
        distill(sourceFile, distillInfo, defs, MainArgs.DistillStage.typed);
        if (flags.outputFile() != null)
          AyaCompiler.saveCompiledCore(flags.outputFile(), moduleResolve, defs, new Serializer.State());
        if (moduleCallback != null) moduleCallback.onModuleTycked(moduleResolve, defs);
      });
    });
  }

  private void distill(
    @NotNull Path sourceFile,
    @Nullable CompilerFlags.DistillInfo flags,
    @NotNull ImmutableSeq<? extends AyaDocile> doc,
    @NotNull MainArgs.DistillStage currentStage
  ) throws IOException {
    if (flags == null || currentStage != flags.distillStage()) return;
    var ayaFileName = sourceFile.getFileName().toString();
    var dotIndex = ayaFileName.indexOf('.');
    var distillDir = sourceFile.resolveSibling(flags.distillDir());
    if (!Files.exists(distillDir)) Files.createDirectories(distillDir);
    var fileName = escape(ayaFileName.substring(0, dotIndex > 0 ? dotIndex : ayaFileName.length()));
    var renderOptions = flags.renderOptions();
    var out = flags.distillFormat().target;
    doWrite(doc, distillDir, flags.distillerOptions(), fileName, out.fileExt,
      (d, hdr) -> renderOptions.render(out, d, hdr));
  }

  private @NotNull String escape(@NotNull String s) {
    // Escape file names, see https://stackoverflow.com/a/41108758/7083401
    return s.replaceAll("[\\\\/:*?\"<>|]", "_");
  }

  private void doWrite(
    ImmutableSeq<? extends AyaDocile> doc, Path distillDir,
    @NotNull DistillerOptions options, String fileName, String fileExt,
    BiFunction<Doc, Boolean, String> toString
  ) throws IOException {
    var docs = MutableList.<Doc>create();
    for (int i = 0; i < doc.size(); i++) {
      var item = doc.get(i);
      // Skip uninteresting items
      var thisDoc = item.toDoc(options);
      docs.append(thisDoc);
      if (item instanceof PrimDef) continue;
      Files.writeString(distillDir.resolve(fileName + "-" + escape(nameOf(i, item)) + fileExt), toString.apply(thisDoc, false));
    }
    Files.writeString(distillDir.resolve(fileName + fileExt), toString.apply(Doc.vcat(docs), true));
  }

  @NotNull private String nameOf(int i, AyaDocile item) {
    return item instanceof Def def ? def.ref().name()
      : item instanceof Decl decl ? decl.ref().name() : String.valueOf(i);
  }
}

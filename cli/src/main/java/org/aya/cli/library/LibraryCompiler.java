// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.tuple.Unit;
import org.aya.api.error.CountingReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.util.InternalException;
import org.aya.api.util.InterruptException;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.json.LibraryDependency;
import org.aya.cli.single.CompilerFlags;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.resolve.module.ModuleLoader;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.serde.Serializer;
import org.aya.util.MutableGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Function;

/**
 * @author kiva
 */
public class LibraryCompiler implements ImportResolver.ImportLoader {
  final @NotNull LibraryConfig library;
  final @NotNull SourceFileLocator locator;

  private final @NotNull CompilerFlags flags;
  private final @NotNull CountingReporter reporter;
  private final @NotNull DynamicSeq<Path> thisModulePath = DynamicSeq.create();
  private final @NotNull DynamicSeq<LibraryCompiler> deps = DynamicSeq.create();
  private final @NotNull ImmutableSeq<LibrarySource> sources;

  public LibraryCompiler(@NotNull Reporter reporter, @NotNull CompilerFlags flags, @NotNull LibraryConfig library) {
    this.reporter = reporter instanceof CountingReporter counting ? counting : new CountingReporter(reporter);
    this.flags = flags;
    this.library = library;
    var srcRoot = library.librarySrcRoot();
    this.locator = new SourceFileLocator.Module(SeqView.of(srcRoot));
    this.sources = collectSource(srcRoot).map(p -> new LibrarySource(this, p));
  }

  public static int compile(@NotNull Reporter reporter, @NotNull CompilerFlags flags, @NotNull Path libraryRoot) throws IOException {
    var config = LibraryConfigData.fromLibraryRoot(libraryRoot);
    var compiler = new LibraryCompiler(reporter, flags, config);
    return compiler.start();
  }

  private @Nullable LibraryConfig depConfig(@NotNull LibraryDependency dep) throws IOException {
    // TODO: test only: dependency resolving should be done in package manager
    if (dep instanceof LibraryDependency.DepFile file)
      return LibraryConfigData.fromDependencyRoot(file.depRoot(), version -> depBuildRoot(dep.depName(), version));
    return null;
  }

  private @NotNull Path depBuildRoot(@NotNull String depName, @NotNull String version) {
    return library.libraryBuildRoot().resolve("deps").resolve(depName + "_" + version);
  }

  private void resolveImports(@NotNull LibrarySource source) throws IOException {
    var owner = source.owner();
    var program = AyaParsing.program(owner.locator, owner.reporter, source.file());
    var finder = new ImportResolver(owner, source);
    finder.resolveStmt(program);
  }

  private @NotNull MutableGraph<LibrarySource> resolveLibraryImports() throws IOException {
    var graph = MutableGraph.<LibrarySource>create();
    reporter.reportString("  [Info] Resolving source file dependency");
    for (var file : sources) {
      resolveImports(file);
      collectDep(graph, file);
    }
    return graph;
  }

  public int start() throws IOException {
    if (flags.modulePaths().isNotEmpty()) reporter.reportString(
      "Warning: command-line specified module path is ignored when compiling libraries.");
    if (flags.distillInfo() != null) reporter.reportString(
      "Warning: command-line specified distill info is ignored when compiling libraries.");
    try {
      make();
    } catch (InternalException e) {
      FileModuleLoader.handleInternalError(e);
      reporter.reportString("Internal error");
      return e.exitCode();
    } catch (InterruptException e) {
      reporter.reportString(e.stage().name() + " interrupted due to:");
      if (flags.interruptedTrace()) e.printStackTrace();
    } finally {
      PrimDef.Factory.INSTANCE.clear();
    }
    if (reporter.noError()) {
      reporter.reportString(flags.message().successNotion());
      return 0;
    } else {
      reporter.reportString(reporter.countToString());
      reporter.reportString(flags.message().failNotion());
      return 1;
    }
  }

  /**
   * Incrementally compiles a library.
   *
   * @return whether the library is up-to-date.
   * @apiNote The return value does not indicate whether the library is compiled successfully.
   */
  private boolean make() throws IOException {
    var anyDepChanged = false;
    for (var dep : library.deps()) {
      var depConfig = depConfig(dep);
      if (depConfig == null) {
        reporter.reportString("Skipping " + dep.depName());
        continue;
      }
      var depCompiler = new LibraryCompiler(reporter, flags, depConfig);
      deps.append(depCompiler);
    }

    for (var dep : deps) {
      var upToDate = dep.make();
      anyDepChanged = anyDepChanged || !upToDate;
      thisModulePath.append(dep.library.libraryOutRoot());
    }

    reporter.reportString("Compiling " + library.name());
    if (anyDepChanged) deleteRecursively(library.libraryOutRoot());

    var srcRoot = library.librarySrcRoot();
    thisModulePath.append(srcRoot);

    var depGraph = resolveLibraryImports();
    return make(depGraph);
  }

  /**
   * @return whether the library is up-to-date.
   */
  private boolean make(
    @NotNull MutableGraph<LibrarySource> depGraph
  ) throws IOException {
    var changed = MutableGraph.<LibrarySource>create();
    var usage = depGraph.transpose();
    depGraph.E().keysView().forEach(s -> {
      if (Timestamp.sourceModified(s))
        collectChanged(usage, s, changed);
    });

    var order = changed.topologicalOrder().view()
      .flatMap(Function.identity())
      .toImmutableLinkedSeq()
      .reversed();
    // ^ top order generated from usage graph should be reversed
    // Only here we generate top order from usage graph just for efficiency
    // (transposing a graph is slower than reversing a linked list).
    // in other cases we always generate top order from dependency graphs
    // because usage graphs are never needed.
    if (order.isEmpty()) {
      reporter.reportString("  [Info] No changes detected, no need to remake");
      return true;
    }
    tyckLibrary(order);
    return false;
  }

  /** Produces tyck order of modules in a library */
  private void tyckLibrary(
    @NotNull ImmutableSeq<LibrarySource> order
  ) throws IOException {
    var thisOutRoot = Files.createDirectories(library.libraryOutRoot());
    var moduleLoader = new CachedModuleLoader(new LibraryModuleLoader(reporter, locator, thisModulePath.view(), thisOutRoot));

    for (var f : order) Files.deleteIfExists(f.coreFile());
    order.forEach(file -> {
      var mod = resolveModule(moduleLoader, file);
      reporter.reportString("  [Tyck] " + mod.thisModule().underlyingFile());
      FileModuleLoader.tyckResolvedModule(mod, reporter,
        (moduleResolve, stmts, defs) -> {
          if (reporter.noError()) saveCompiledCore(file, moduleResolve, defs);
        },
        null);
    });
  }

  private @NotNull ResolveInfo resolveModule(
    @NotNull ModuleLoader moduleLoader,
    @NotNull LibrarySource file
  ) {
    var mod = file.moduleName();
    System.out.printf("  [Resolve] %s (%s)%n", mod.joinToString("::"), file.file());
    var resolveInfo = moduleLoader.load(mod);
    if (resolveInfo == null) throw new IllegalStateException("Unable to load module: " + mod);
    return resolveInfo;
  }

  private @Nullable LibrarySource findModuleFile(@NotNull ImmutableSeq<String> mod) {
    return sources.find(s -> {
      var checkMod = s.moduleName();
      return checkMod.equals(mod);
    }).getOrNull();
  }

  private void saveCompiledCore(@NotNull LibrarySource file, @NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<Def> defs) {
    try (var outputStream = coreWriter(file)) {
      var serDefs = defs.map(def -> def.accept(new Serializer(new Serializer.State()), Unit.unit()));
      var compiled = CompiledAya.from(resolveInfo, serDefs);
      outputStream.writeObject(compiled);
      Timestamp.update(file);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private @NotNull ObjectOutputStream coreWriter(@NotNull LibrarySource file) throws IOException {
    var coreFile = file.coreFile();
    Files.createDirectories(coreFile.getParent());
    return new ObjectOutputStream(Files.newOutputStream(file.coreFile()));
  }

  private static @NotNull ImmutableSeq<Path> collectSource(@NotNull Path srcRoot) {
    try (var walk = Files.walk(srcRoot)) {
      return walk.filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().endsWith(".aya"))
        .collect(ImmutableSeq.factory());
    } catch (IOException e) {
      return ImmutableSeq.empty();
    }
  }

  private static void collectDep(@NotNull MutableGraph<LibrarySource> dep, @NotNull LibrarySource info) {
    dep.suc(info).appendAll(info.imports());
    info.imports().forEach(i -> collectDep(dep, i));
  }

  private static void collectChanged(
    @NotNull MutableGraph<LibrarySource> usage,
    @NotNull LibrarySource changed,
    @NotNull MutableGraph<LibrarySource> changedGraph
  ) {
    if (changedGraph.E().containsKey(changed)) return;
    changedGraph.suc(changed).appendAll(usage.suc(changed));
    usage.suc(changed).forEach(dep -> collectChanged(usage, dep, changedGraph));
  }

  private static void deleteRecursively(@NotNull Path path) throws IOException {
    if (!Files.exists(path)) return;
    try (var walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder())
        .collect(ImmutableSeq.factory())
        .forEachChecked(Files::deleteIfExists);
    }
  }

  @Override public @NotNull LibrarySource load(@NotNull ImmutableSeq<String> mod) {
    var file = findModuleFile(mod);
    if (file == null) for (var dep : deps) {
      file = dep.findModuleFile(mod);
      if (file != null) break;
    }
    if (file == null) throw new IllegalArgumentException("No library owns module: " + mod);
    try {
      resolveImports(file);
    } catch (IOException e) {
      throw new RuntimeException("Cannot load imported module " + mod, e);
    }
    return file;
  }
}

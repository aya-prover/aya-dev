// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicLinkedSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableSet;
import kala.tuple.Unit;
import org.aya.api.error.CountingReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.json.LibraryDependency;
import org.aya.cli.single.CliReporter;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.resolve.module.ModuleLoader;
import org.aya.core.def.Def;
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
import java.util.stream.IntStream;

/**
 * @author kiva
 */
public record LibraryCompiler(@NotNull Path buildRoot, boolean unicode) {
  public static int compile(@NotNull Path libraryRoot, boolean unicode) throws IOException {
    var config = LibraryConfigData.fromLibraryRoot(libraryRoot);
    new LibraryCompiler(config.libraryBuildRoot(), unicode).make(config);
    return 0;
  }

  private @Nullable LibraryConfig depConfig(@NotNull LibraryDependency dep) throws IOException {
    // TODO: test only: dependency resolving should be done in package manager
    if (dep instanceof LibraryDependency.DepFile file)
      return LibraryConfigData.fromDependencyRoot(file.depRoot(), version -> depBuildRoot(dep.depName(), version));
    return null;
  }

  private @NotNull Path depBuildRoot(@NotNull String depName, @NotNull String version) {
    return buildRoot.resolve("deps").resolve(depName + "_" + version);
  }

  private @NotNull LibrarySource resolveImports(
    @NotNull LibraryConfig owner,
    @NotNull Reporter reporter,
    @NotNull SourceFileLocator locator,
    @NotNull ImportResolver.ImportLoader loader,
    @NotNull Path path
  ) throws IOException {
    var program = AyaParsing.program(locator, reporter, path);
    var source = new LibrarySource(owner, path);
    var finder = new ImportResolver(loader, source);
    finder.resolveStmt(program);
    return source;
  }

  private @NotNull MutableGraph<LibrarySource> resolveImports(
    @NotNull LibraryConfig owner,
    @NotNull Reporter reporter,
    @NotNull SourceFileLocator locator,
    @NotNull ImportResolver.ImportLoader loader,
    @NotNull LibraryConfig config
  ) throws IOException {
    System.out.println("  [Info] Collecting source files");
    var graph = MutableGraph.<LibrarySource>create();
    var sources = collectSource(config.librarySrcRoot());
    System.out.println("  [Info] Resolving source file dependency");
    for (var file : sources) {
      var resolve = resolveImports(owner, reporter, locator, loader, file);
      collectDep(graph, resolve);
    }
    return graph;
  }

  /**
   * Incrementally compiles a library.
   *
   * @return whether the library is up-to-date.
   * @apiNote The return value does not indicate whether the library is compiled successfully.
   */
  private boolean make(@NotNull LibraryConfig config) throws IOException {
    // TODO[kiva]: move to package manager
    var thisModulePath = DynamicSeq.<Path>create();
    var locatorPath = DynamicSeq.<Path>create();
    var anyDepChanged = false;
    for (var dep : config.deps()) {
      var depConfig = depConfig(dep);
      if (depConfig == null) {
        System.out.println("Skipping " + dep.depName());
        continue;
      }
      var upToDate = make(depConfig);
      anyDepChanged = anyDepChanged || !upToDate;
      thisModulePath.append(depConfig.libraryOutRoot());
      locatorPath.append(depConfig.librarySrcRoot());
    }

    System.out.println("Compiling " + config.name());
    // TODO: be incremental when dependencies changed
    if (anyDepChanged) deleteRecursively(config.libraryOutRoot());

    var srcRoot = config.librarySrcRoot();
    var thisOutRoot = Files.createDirectories(config.libraryOutRoot());
    thisModulePath.append(srcRoot);
    locatorPath.prepend(srcRoot);

    var reporter = CliReporter.stdio(unicode);
    var locator = new SourceFileLocator.Module(locatorPath.view());
    var timestamp = new Timestamp(locator, thisOutRoot);

    var resolveLoader = new LibraryImportLoader(config, this, reporter, locator, locatorPath.view());
    var depGraph = resolveImports(config, reporter, locator, resolveLoader, config);

    var moduleLoader = new CachedModuleLoader(new LibraryModuleLoader(reporter, locator, thisModulePath.view(), thisOutRoot));
    return make(reporter, moduleLoader, depGraph, timestamp);
  }

  /**
   * @return whether the library is up-to-date.
   */
  private boolean make(
    @NotNull Reporter reporter,
    @NotNull ModuleLoader moduleLoader,
    @NotNull MutableGraph<LibrarySource> depGraph,
    @NotNull Timestamp timestamp
  ) throws IOException {
    var changed = MutableSet.<LibrarySource>create();
    var usage = depGraph.transpose();
    depGraph.E().keysView().forEach(s -> {
      if (timestamp.sourceModified(s.file()))
        collectChanged(usage, s, changed);
    });

    if (changed.isEmpty()) {
      System.out.println("  [Info] No changes detected, no need to remake");
      return true;
    }

    var changedDepGraph = MutableGraph.<LibrarySource>create();
    changed.forEach(c -> collectDep(changedDepGraph, c));

    var order = changedDepGraph.topologicalOrder().view()
      .flatMap(Function.identity())
      .map(LibrarySource::file)
      .toImmutableSeq();
    tyckLibrary(reporter, moduleLoader, timestamp.locator, timestamp, order);
    return false;
  }

  /** Produces tyck order of modules in a library */
  private void tyckLibrary(
    @NotNull Reporter reporter,
    @NotNull ModuleLoader moduleLoader,
    @NotNull SourceFileLocator locator,
    @NotNull Timestamp timestamp,
    @NotNull ImmutableSeq<Path> order
  ) throws IOException {
    var coreSaver = new CoreSaver(timestamp);
    for (var f : order) Files.deleteIfExists(coreFile(locator, f, timestamp.outRoot));
    order.forEach(file -> {
      var mod = resolveModule(moduleLoader, locator, file);
      if (mod.thisProgram().isEmpty()) {
        System.out.println("  [Reuse] " + mod.thisModule().underlyingFile());
        return;
      }
      System.out.println("  [Tyck] " + mod.thisModule().underlyingFile());
      var counting = new CountingReporter(reporter);
      FileModuleLoader.tyckResolvedModule(mod, counting,
        (moduleResolve, stmts, defs) -> {
          if (counting.noError()) coreSaver.saveCompiledCore(moduleResolve, defs);
        },
        null);
    });
  }

  private @NotNull ResolveInfo resolveModule(
    @NotNull ModuleLoader moduleLoader,
    @NotNull SourceFileLocator locator,
    @NotNull Path file
  ) {
    var mod = moduleName(locator, file);
    System.out.printf("  [Resolve] %s (%s)%n", mod.joinToString("::"), file);
    var resolveInfo = moduleLoader.load(mod);
    if (resolveInfo == null) throw new IllegalStateException("Unable to load module: " + mod);
    return resolveInfo;
  }

  private static @NotNull Path coreFile(
    @NotNull SourceFileLocator locator, @NotNull Path file, @NotNull Path outRoot
  ) throws IOException {
    var raw = outRoot.resolve(locator.displayName(file));
    var core = raw.resolveSibling(raw.getFileName().toString() + "c");
    Files.createDirectories(core.getParent());
    return core;
  }

  record Timestamp(@NotNull SourceFileLocator locator, @NotNull Path outRoot) {
    public boolean sourceModified(@NotNull Path file) {
      try {
        var core = coreFile(locator, file, outRoot);
        if (!Files.exists(core)) return true;
        return Files.getLastModifiedTime(file)
          .compareTo(Files.getLastModifiedTime(core)) > 0;
      } catch (IOException ignore) {
        return true;
      }
    }

    public void update(@NotNull Path file) {
      try {
        var core = coreFile(locator, file, outRoot);
        Files.setLastModifiedTime(core, Files.getLastModifiedTime(file));
      } catch (IOException ignore) {
      }
    }
  }

  record CoreSaver(@NotNull Timestamp timestamp) {
    private void saveCompiledCore(@NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<Def> defs) {
      var sourcePath = resolveInfo.canonicalPath();
      try (var outputStream = openCompiledCore(sourcePath)) {
        var serDefs = defs.map(def -> def.accept(new Serializer(new Serializer.State()), Unit.unit()));
        var compiled = CompiledAya.from(resolveInfo, serDefs);
        outputStream.writeObject(compiled);
        timestamp.update(sourcePath);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    private @NotNull ObjectOutputStream openCompiledCore(@NotNull Path sourcePath) throws IOException {
      return new ObjectOutputStream(Files.newOutputStream(
        coreFile(timestamp.locator, sourcePath, timestamp.outRoot)));
    }
  }

  private static @NotNull DynamicLinkedSeq<Path> collectSource(@NotNull Path srcRoot) throws IOException {
    try (var walk = Files.walk(srcRoot)) {
      return walk.filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().endsWith(".aya"))
        .map(ResolveInfo::canonicalize)
        .collect(DynamicLinkedSeq.factory());
    }
  }

  private static void collectDep(@NotNull MutableGraph<LibrarySource> dep, @NotNull LibrarySource info) {
    dep.suc(info).appendAll(info.imports());
    info.imports().forEach(i -> {
      collectDep(dep, i);
    });
  }

  private static void collectChanged(
    @NotNull MutableGraph<LibrarySource> usage,
    @NotNull LibrarySource changed,
    @NotNull MutableSet<LibrarySource> changedList
  ) {
    if (changedList.contains(changed)) return;
    changedList.add(changed);
    usage.suc(changed).forEach(dep -> collectChanged(usage, dep, changedList));
  }

  private static @NotNull ImmutableSeq<String> moduleName(@NotNull SourceFileLocator locator, @NotNull Path file) {
    var display = locator.displayName(file);
    var displayNoExt = display.resolveSibling(display.getFileName().toString().replaceAll("\\.aya", ""));
    return IntStream.range(0, displayNoExt.getNameCount())
      .mapToObj(i -> displayNoExt.getName(i).toString())
      .collect(ImmutableSeq.factory());
  }

  private static void deleteRecursively(@NotNull Path path) throws IOException {
    if (!Files.exists(path)) return;
    try (var walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder())
        .collect(ImmutableSeq.factory())
        .forEachChecked(Files::deleteIfExists);
    }
  }

  record LibraryImportLoader(
    @NotNull LibraryConfig owner,
    @NotNull LibraryCompiler compiler,
    @NotNull Reporter reporter,
    @NotNull SourceFileLocator locator,
    @NotNull SeqView<Path> thisModulePath
  ) implements ImportResolver.ImportLoader {
    @Override public @NotNull LibrarySource load(@NotNull ImmutableSeq<String> mod) {
      var file = LibraryModuleLoader.resolveFile(thisModulePath, mod);
      if (file == null) throw new IllegalArgumentException("incomplete module path");
      try {
        return compiler.resolveImports(owner, reporter, locator, this, file);
      } catch (IOException e) {
        throw new RuntimeException("Cannot load imported module " + mod, e);
      }
    }
  }
}

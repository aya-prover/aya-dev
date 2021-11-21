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

/**
 * @author kiva
 */
public class LibraryCompiler implements ImportResolver.ImportLoader {
  final @NotNull LibraryConfig library;
  final @NotNull SourceFileLocator locator;

  private final @NotNull Reporter reporter;
  private final @NotNull Path buildRoot;
  private final @NotNull Timestamp timestamp;
  private final boolean unicode;
  private final @NotNull DynamicSeq<Path> thisModulePath = DynamicSeq.create();
  private final @NotNull DynamicSeq<LibraryCompiler> deps = DynamicSeq.create();
  private final @NotNull DynamicSeq<LibrarySource> resolvedSources = DynamicSeq.create();

  public LibraryCompiler(@NotNull LibraryConfig library, @NotNull Path buildRoot, boolean unicode) {
    this.library = library;
    this.buildRoot = buildRoot;
    this.unicode = unicode;
    this.reporter = CliReporter.stdio(unicode);
    this.locator = new SourceFileLocator.Module(SeqView.of(library.librarySrcRoot()));
    this.timestamp = new Timestamp(locator, library.libraryOutRoot());
  }

  public static int compile(@NotNull Path libraryRoot, boolean unicode) throws IOException {
    var config = LibraryConfigData.fromLibraryRoot(libraryRoot);
    new LibraryCompiler(config, config.libraryBuildRoot(), unicode).make();
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
    @NotNull LibraryCompiler owner,
    @NotNull Path path
  ) throws IOException {
    var program = AyaParsing.program(owner.locator, owner.reporter, path);
    var source = new LibrarySource(owner, path);
    var finder = new ImportResolver(owner, source);
    finder.resolveStmt(program);
    return source;
  }

  private @NotNull MutableGraph<LibrarySource> resolveLibraryImports() throws IOException {
    var graph = MutableGraph.<LibrarySource>create();
    var sources = collectSource(library.librarySrcRoot());
    System.out.println("  [Info] Resolving source file dependency");
    for (var file : sources) {
      var resolve = resolveImports(this, file);
      resolvedSources.append(resolve);
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
  private boolean make() throws IOException {
    // TODO[kiva]: move to package manager
    var anyDepChanged = false;
    for (var dep : library.deps()) {
      var depConfig = depConfig(dep);
      if (depConfig == null) {
        System.out.println("Skipping " + dep.depName());
        continue;
      }
      var depCompiler = new LibraryCompiler(depConfig, library.libraryBuildRoot(), unicode);
      deps.append(depCompiler);
    }

    for (var dep : deps) {
      var upToDate = dep.make();
      anyDepChanged = anyDepChanged || !upToDate;
      thisModulePath.append(dep.library.libraryOutRoot());
    }

    System.out.println("Compiling " + library.name());
    // TODO: be incremental when dependencies changed
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
      if (s.owner().timestamp.sourceModified(s.file()))
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
      System.out.println("  [Info] No changes detected, no need to remake");
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
    var coreSaver = new CoreSaver(timestamp);

    for (var f : order) Files.deleteIfExists(coreFile(locator, f.file(), timestamp.outRoot()));
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
    @NotNull LibrarySource file
  ) {
    var mod = file.moduleName();
    System.out.printf("  [Resolve] %s (%s)%n", mod.joinToString("::"), file.file());
    var resolveInfo = moduleLoader.load(mod);
    if (resolveInfo == null) throw new IllegalStateException("Unable to load module: " + mod);
    return resolveInfo;
  }

  private @Nullable LibrarySource findModuleFile(@NotNull ImmutableSeq<String> mod) {
    return resolvedSources.find(s -> {
      var checkMod = s.moduleName();
      return checkMod.equals(mod);
    }).getOrNull();
  }

  public static @NotNull Path coreFile(
    @NotNull SourceFileLocator locator, @NotNull Path file, @NotNull Path outRoot
  ) throws IOException {
    var raw = outRoot.resolve(locator.displayName(file));
    var core = raw.resolveSibling(raw.getFileName().toString() + "c");
    Files.createDirectories(core.getParent());
    return core;
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
        coreFile(timestamp.locator(), sourcePath, timestamp.outRoot())));
    }
  }

  private static @NotNull ImmutableSeq<Path> collectSource(@NotNull Path srcRoot) {
    try (var walk = Files.walk(srcRoot)) {
      return walk.filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().endsWith(".aya"))
        .map(ResolveInfo::canonicalize)
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
    if (file == null) {
      for (var dep : deps) {
        file = dep.findModuleFile(mod);
        if (file != null) break;
      }
    }
    if (file == null) throw new IllegalArgumentException("invalid module path");
    try {
      return resolveImports(file.owner(), file.file());
    } catch (IOException e) {
      throw new RuntimeException("Cannot load imported module " + mod, e);
    }
  }
}

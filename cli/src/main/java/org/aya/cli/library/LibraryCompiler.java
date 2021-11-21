// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicLinkedSeq;
import kala.collection.mutable.DynamicSeq;
import kala.control.Try;
import kala.tuple.Unit;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.json.LibraryDependency;
import org.aya.cli.single.CliReporter;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.resolve.module.ModuleListLoader;
import org.aya.concrete.resolve.module.ModuleLoader;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.Def;
import org.aya.core.serde.CompiledAya;
import org.aya.core.serde.Serializer;
import org.aya.util.MutableGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

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

  private @NotNull ResolveInfo resolveModule(
    @NotNull Reporter reporter,
    @NotNull ModuleLoader moduleLoader,
    @NotNull SourceFileLocator locator,
    @NotNull Path file
  ) {
    System.out.println("  [Scan] " + file);
    var ctx = new EmptyContext(reporter, file).derive(file.getFileName().toString());
    var program = Try.of(() -> AyaParsing.program(locator, reporter, file))
      .getOrThrow(() -> new RuntimeException("Unable to parse " + file));
    var resolveInfo = new ResolveInfo(ctx, program, new AyaBinOpSet(reporter));
    Stmt.resolve(program, resolveInfo, moduleLoader);
    return resolveInfo;
  }

  /** Produces tyck order of modules in a library */
  private @NotNull ImmutableSeq<ImmutableSeq<ResolveInfo>> resolveLibrary(
    @NotNull Reporter reporter,
    @NotNull ModuleLoader moduleLoader,
    @NotNull SourceFileLocator locator,
    @NotNull LibraryConfig config
  ) throws IOException {
    var srcRoot = config.librarySrcRoot();
    System.out.println("  [Info] Collecting source files");
    var remaining = collect(srcRoot);
    if (!remaining.isNotEmpty()) return ImmutableSeq.empty();

    // Because we randomly pick one module to start resolving, the randomly picked module `A`
    // may be imported by other module `B` (which will be picked from `remaining` in future loops),
    // in which case the module loader will _recreate_ and cache the ResolveInfo `A` when
    // resolving `B` instead of using the one that was manually created by us.
    // To avoid duplicated ResolveInfo instances of a same module, we need to record
    // manually created ones, filter out modules that are not imported by others
    // (which we call entry items), and add them to the dependency graph as root vertexes.

    var selfMade = DynamicLinkedSeq.<ResolveInfo>create();
    var proceed = DynamicLinkedSeq.<ResolveInfo>create();
    while (remaining.isNotEmpty()) {
      var file = remaining.pop();
      var resolveInfo = resolveModule(reporter, moduleLoader, locator, file);
      resolveInfo.imports().forEach(r -> {
        var path = r.canonicalPath();
        System.out.println("    [Dep]: " + r.thisModule().underlyingFile());
        // dependencies are resolved by module loader
        remaining.removeAll(path::equals);
        proceed.push(r);
      });
      selfMade.push(resolveInfo);
    }

    selfMade.view().filter(sm -> proceed.find(proc -> proc.canonicalPath().equals(sm.canonicalPath())).isEmpty())
      .forEach(sm -> {
        System.out.printf("  [Info] Entry: %s%n", sm.canonicalPath());
        proceed.push(sm);
      });

    var graph = MutableGraph.<ResolveInfo>create();
    proceed.forEach(p -> buildGraph(graph, p));
    return graph.topologicalOrder();
  }

  private DynamicLinkedSeq<Path> collect(@NotNull Path srcRoot) throws IOException {
    return Files.walk(srcRoot).filter(Files::isRegularFile)
      .map(ResolveInfo::canonicalize)
      .collect(DynamicLinkedSeq.factory());
  }

  private void buildGraph(@NotNull MutableGraph<ResolveInfo> graph, @NotNull ResolveInfo info) {
    graph.suc(info).appendAll(info.imports());
    info.imports().forEach(i -> buildGraph(graph, i));
  }

  private void make(@NotNull LibraryConfig config) throws IOException {
    // TODO[kiva]: move to package manager
    var modulePath = DynamicSeq.<Path>create();
    var locatorPath = DynamicSeq.<Path>create();
    for (var dep : config.deps()) {
      var depConfig = depConfig(dep);
      if (depConfig == null) {
        System.out.println("Skipping " + dep.depName());
        continue;
      }
      make(depConfig);
      modulePath.append(depConfig.libraryOutRoot());
      locatorPath.append(depConfig.librarySrcRoot());
    }

    System.out.println("Compiling " + config.name());

    var srcRoot = config.librarySrcRoot();
    var outRoot = config.libraryOutRoot();
    modulePath.prepend(Files.createDirectories(outRoot));
    modulePath.append(srcRoot);
    locatorPath.prepend(srcRoot);
    var locator = new SourceFileLocator.Module(locatorPath.view());

    var reporter = CliReporter.stdio(unicode);
    var loader = new ModuleListLoader(modulePath.view().map(path ->
      new CachedModuleLoader(new LibraryModuleLoader(locator, path, reporter))).toImmutableSeq());
    var SCCs = resolveLibrary(reporter, loader, locator, config);

    var timestamp = new Timestamp(locator, outRoot);
    var coreSaver = new CoreSaver(timestamp);
    SCCs.flatMap(Function.identity()).forEach(mod -> {
      System.out.println("  [Make] " + mod.thisModule().underlyingFile());
      FileModuleLoader.tyckResolvedModule(mod, reporter,
        (moduleResolve, stmts, defs) -> coreSaver.saveCompiledCore(moduleResolve, defs),
        null);
    });
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
    public boolean needRecompile(@NotNull Path file) {
      // TODO[kiva]: build file dependency and trigger recompile
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
        var compiled = CompiledAya.from(serDefs);
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
}

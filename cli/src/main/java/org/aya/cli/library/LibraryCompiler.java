// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli.library;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.tuple.Unit;
import org.aya.api.error.SourceFileLocator;
import org.aya.cli.single.CliReporter;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.Def;
import org.aya.core.serde.Serializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author kiva
 */
public record LibraryCompiler(@NotNull Path buildRoot) {
  public static int compile(@NotNull Path libraryRoot) throws IOException {
    var config = LibraryConfigData.fromLibraryRoot(libraryRoot);
    new LibraryCompiler(config.libraryBuildRoot()).make(config);
    return 0;
  }

  private @Nullable LibraryConfig depConfig(@NotNull LibraryDependency dep) throws IOException {
    // TODO: test only: dependency resolving should be done in package manager
    if (dep instanceof LibraryDependency.DepFile file)
      return LibraryConfigData.fromDependencyRoot(file.depRoot(), depBuildRoot(dep.depName()));
    return null;
  }

  private @NotNull Path depBuildRoot(@NotNull String depName) throws IOException {
    return Files.createDirectories(buildRoot.resolve("deps").resolve(depName));
  }

  private void make(@NotNull LibraryConfig config) throws IOException {
    // TODO[kiva]: move to package manager
    var compiledModulePath = Buffer.<Path>of();
    var modulePath = Buffer.<Path>of();
    for (var dep : config.deps()) {
      var depConfig = depConfig(dep);
      if (depConfig == null) {
        System.out.println("Skipping " + dep.depName());
        continue;
      }
      make(depConfig);
      compiledModulePath.append(depConfig.libraryOutRoot());
      modulePath.append(depConfig.librarySrcRoot());
    }

    System.out.println("Compiling " + config.name());

    var srcRoot = config.librarySrcRoot();
    var outRoot = config.libraryOutRoot();
    compiledModulePath.prepend(Files.createDirectories(outRoot));
    compiledModulePath.append(srcRoot);
    modulePath.prepend(srcRoot);

    Files.walk(srcRoot).filter(Files::isRegularFile)
      .forEach(file -> callSingleFileCompiler(file, compiledModulePath, modulePath, outRoot));
  }

  private void callSingleFileCompiler(
    @NotNull Path file,
    @NotNull Buffer<Path> compiledModulePath,
    @NotNull Buffer<Path> modulePath,
    @NotNull Path outRoot
  ) {
    var locator = new SourceFileLocator.Module(modulePath.view());
    var relativeToLibRoot = locator.displayName(file);
    var timestamp = new Timestamp(locator, outRoot);
    System.out.print(" -- " + relativeToLibRoot + " : ");
    if (!timestamp.needRecompile(file)) {
      System.out.println("UP-TO-DATE");
      return;
    }
    var compiler = new SingleFileCompiler(CliReporter.INSTANCE, locator, null);
    try {
      compiler.compile(file, new CompilerFlags(
        CompilerFlags.Message.EMOJI, false, null, compiledModulePath
      ), new CoreSaver(locator, outRoot, timestamp));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static @NotNull Path coreFile(
    @NotNull SourceFileLocator locator, @NotNull Path file, @NotNull Path outRoot
  ) throws IOException {
    var raw = outRoot.resolve(locator.displayName(file));
    var core = raw.resolveSibling(raw.getFileName().toString().replace(".aya", ".ayac"));
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

  record CoreSaver(
    @NotNull SourceFileLocator locator,
    @NotNull Path outRoot,
    @NotNull Timestamp timestamp
  ) implements FileModuleLoader.FileModuleLoaderCallback {
    @Override
    public void onResolved(@NotNull Path sourcePath, @NotNull ImmutableSeq<Stmt> stmts) {
    }

    @Override
    public void onTycked(@NotNull Path sourcePath, @NotNull ImmutableSeq<Stmt> stmts, @NotNull ImmutableSeq<Def> defs) {
      saveCompiledCore(sourcePath, defs);
      timestamp.update(sourcePath);
    }

    private void saveCompiledCore(@NotNull Path sourcePath, ImmutableSeq<Def> defs) {
      try (var outputStream = openCompiledCore(sourcePath)) {
        var serDefs = defs.map(def -> def.accept(new Serializer(new Serializer.State()), Unit.unit()))
          .collect(Seq.factory());
        outputStream.writeObject(serDefs);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    private @NotNull ObjectOutputStream openCompiledCore(@NotNull Path sourcePath) throws IOException {
      return new ObjectOutputStream(Files.newOutputStream(
        coreFile(locator, sourcePath, outRoot)));
    }
  }
}

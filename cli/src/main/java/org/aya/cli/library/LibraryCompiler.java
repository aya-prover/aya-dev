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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
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

  private @NotNull LibraryConfig depConfig(@NotNull LibraryDependency dep) throws IOException {
    if (!(dep instanceof LibraryDependency.DepFile file))
      throw new UnsupportedOperationException("WIP");
    return LibraryConfigData.fromDependencyRoot(file.depRoot(), depBuildRoot(dep.depName()));
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
      int status = compiler.compile(file, new CompilerFlags(
        CompilerFlags.Message.EMOJI, false, null, compiledModulePath
      ), new CoreSaver(locator, outRoot, timestamp));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static @NotNull Path compiledCoreExt(@NotNull Path raw) {
    return raw.resolveSibling(raw.getFileName().toString().replace(".aya", ".ayac"));
  }

  private static @NotNull Path compiledCoreFile(@NotNull SourceFileLocator locator,
                                                @NotNull Path file, @NotNull Path outRoot) throws IOException {
    var core = compiledCoreExt(outRoot.resolve(locator.displayName(file)));
    Files.createDirectories(core.getParent());
    return core;
  }

  record Timestamp(@NotNull SourceFileLocator locator, @NotNull Path outRoot) {
    public boolean needRecompile(@NotNull Path file) {
      // TODO[kiva]: build file dependency and trigger recompile
      try {
        var core = compiledCoreFile(locator, file, outRoot);
        if (!Files.exists(core)) return true;
        return Files.getLastModifiedTime(file)
          .compareTo(Files.getLastModifiedTime(core)) > 0;
      } catch (IOException ignore) {
        return true;
      }
    }

    public void update(@NotNull Path file) {
      try {
        var core = compiledCoreFile(locator, file, outRoot);
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
      var relativeToLibRoot = locator.displayName(sourcePath);
      saveCompiledCore(outRoot, relativeToLibRoot, defs);
      timestamp.update(sourcePath);
    }

    private void saveCompiledCore(@NotNull Path outRoot, Path relativeToLibRoot, ImmutableSeq<Def> defs) {
      try (var outputStream = new ObjectOutputStream(openCompiledCore(outRoot, relativeToLibRoot))) {
        var serDefs = defs.map(def -> def.accept(new Serializer(new Serializer.State()), Unit.unit()))
          .collect(Seq.factory());
        outputStream.writeObject(serDefs);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    private @NotNull OutputStream openCompiledCore(@NotNull Path outRoot, @NotNull Path relativeToLibRoot) throws IOException {
      return Files.newOutputStream(buildOutputName(outRoot, relativeToLibRoot));
    }

    private @NotNull Path buildOutputName(@NotNull Path outRoot, @NotNull Path relativeToLibRoot) throws IOException {
      var fixed = compiledCoreExt(outRoot.resolve(relativeToLibRoot));
      Files.createDirectories(fixed.getParent());
      return fixed;
    }
  }
}

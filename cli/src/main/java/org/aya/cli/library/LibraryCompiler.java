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

    // TODO[kiva]: be incremental
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
    var displayName = locator.displayName(file);
    System.out.println(" -- " + displayName);
    var compiler = new SingleFileCompiler(CliReporter.INSTANCE, locator, null);
    try {
      int status = compiler.compile(file, new CompilerFlags(
        CompilerFlags.Message.EMOJI, false, null, compiledModulePath
      ), new CoreSaver(locator, outRoot));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  record CoreSaver(
    @NotNull SourceFileLocator locator,
    @NotNull Path outRoot
  ) implements FileModuleLoader.FileModuleLoaderCallback {
    @Override
    public void onResolved(@NotNull Path sourcePath, @NotNull ImmutableSeq<Stmt> stmts) {
    }

    @Override
    public void onTycked(@NotNull Path sourcePath, @NotNull ImmutableSeq<Stmt> stmts, @NotNull ImmutableSeq<Def> defs) {
      var relativeToLibraryRoot = locator.displayName(sourcePath);
      saveCompiledCore(outRoot, relativeToLibraryRoot, defs);
    }

    private void saveCompiledCore(@NotNull Path outRoot, Path displayName, ImmutableSeq<Def> defs) {
      try (var outputStream = new ObjectOutputStream(openCompiledCore(outRoot, displayName))) {
        var serDefs = defs.map(def -> def.accept(new Serializer(new Serializer.State()), Unit.unit()))
          .collect(Seq.factory());
        outputStream.writeObject(serDefs);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    private @NotNull OutputStream openCompiledCore(@NotNull Path outRoot, @NotNull Path displayName) throws IOException {
      return Files.newOutputStream(buildOutputName(outRoot, displayName));
    }

    private @NotNull Path buildOutputName(@NotNull Path outRoot, @NotNull Path displayName) throws IOException {
      var raw = outRoot.resolve(displayName);
      var fixed = raw.resolveSibling(raw.getFileName().toString().replace(".aya", ".ayac"));
      Files.createDirectories(fixed.getParent());
      return fixed;
    }
  }
}

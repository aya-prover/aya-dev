// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import org.aya.primitive.PrimFactory;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.error.ModNotFoundException;
import org.aya.syntax.AyaFiles;
import org.aya.syntax.GenericAyaFile;
import org.aya.syntax.GenericAyaParser;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.position.SourceFileLocator;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public record FileModuleLoader(
  @NotNull SourceFileLocator locator,
  @NotNull Path basePath,
  @Override @NotNull Reporter reporter,
  @NotNull GenericAyaParser parser,
  @NotNull GenericAyaFile.Factory fileManager,
  @NotNull PrimFactory primFactory
) implements ModuleLoader {
  public FileModuleLoader(
    @NotNull SourceFileLocator locator, @NotNull Path basePath, @NotNull Reporter reporter,
    @NotNull GenericAyaParser parser, @NotNull GenericAyaFile.Factory fileManager
  ) {
    this(locator, basePath, reporter, parser, fileManager, new PrimFactory());
  }

  @Override public @NotNull ResolveInfo load(@NotNull ModulePath path, @NotNull ModuleLoader recurseLoader)
    throws Context.ResolvingInterruptedException, ModNotFoundException {
    var sourcePath = AyaFiles.resolveAyaSourceFile(basePath, path.module());
    try {
      var program = fileManager.createAyaFile(locator, sourcePath).parseMe(parser);
      var context = new EmptyContext(reporter, sourcePath).derive(path);
      var info = resolveModule(primFactory, context, program, recurseLoader);
      return tyckModule(info, null);
    } catch (IOException e) {
      throw new ModNotFoundException();
    }
  }

  @Override public boolean existsFileLevelModule(@NotNull ModulePath path) {
    try {
      var sourcePath = AyaFiles.resolveAyaSourceFile(basePath, path.module());
      return Files.exists(sourcePath);
    } catch (InvalidPathException e) {
      return false;
    }
  }
}

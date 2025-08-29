// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.source;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.range.primitive.IntRange;
import org.aya.cli.utils.LiterateData;
import org.aya.intellij.GenericNode;
import org.aya.literate.Literate;
import org.aya.pretty.doc.Doc;
import org.aya.producer.NodedAyaProgram;
import org.aya.resolve.ResolveInfo;
import org.aya.syntax.AyaFiles;
import org.aya.syntax.GenericAyaFile;
import org.aya.syntax.GenericAyaParser;
import org.aya.syntax.GenericAyaProgram;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.FileUtil;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourceFile;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A source file may or may not be tycked.
 */
@Debug.Renderer(text = "displayPath()")
public class LibrarySource implements GenericAyaFile {
  public final @NotNull LibraryOwner owner;
  public final @NotNull Path underlyingFile;
  public final boolean isLiterate;
  public final @NotNull MutableList<LibrarySource> imports = MutableList.create();
  GenericNode<?> rootNode;
  /// Initialized after parse
  ImmutableSeq<Stmt> program;
  /// Initialized after tyck
  ImmutableSeq<TyckDef> tycked;
  /// Initialized after resolve
  ResolveInfo resolveInfo;
  LiterateData literateData;
  ModulePath moduleNameCache;

  public LibrarySource(@NotNull LibraryOwner owner, @NotNull Path underlyingFile, boolean isLiterate) {
    this.owner = owner;
    this.underlyingFile = underlyingFile;
    this.isLiterate = isLiterate;
  }

  public static @NotNull LibrarySource create(@NotNull LibraryOwner owner, @NotNull Path file) {
    var underlyingFile = FileUtil.canonicalize(file);
    return new LibrarySource(owner, underlyingFile, AyaFiles.isLiterate(underlyingFile));
  }

  public @NotNull ModulePath moduleName() {
    if (moduleNameCache != null) return moduleNameCache;
    var name = computeModuleName();
    moduleNameCache = name;
    return name;
  }

  public ImmutableSeq<Stmt> program() { return program; }
  public ResolveInfo resolveInfo() { return resolveInfo; }
  public GenericNode<?> rootNode() { return rootNode; }
  public ImmutableSeq<TyckDef> tycked() { return tycked; }
  public void resolveInfo(ResolveInfo resolveInfo) {
    this.resolveInfo = resolveInfo;
  }

  /// @return true if some actions are taken
  public boolean clearTyckData() {
    if (tycked == null) return false;
    resolveInfo = null;
    literateData = null;
    tycked = null;
    return true;
  }

  public void clearAllData() {
    clearTyckData();
    program = null;
    imports.clear();
  }

  private @NotNull ModulePath computeModuleName() {
    if (resolveInfo != null) return resolveInfo.modulePath();
    var display = displayPath();
    var displayNoExt = display.resolveSibling(AyaFiles.stripAyaSourcePostfix(display.getFileName().toString()));
    return new ModulePath(IntRange.closedOpen(0, displayNoExt.getNameCount())
      .mapToObjTo(MutableList.create(), i -> displayNoExt.getName(i).toString())
      .toSeq());
  }

  public @NotNull Path displayPath() {
    return owner.locator().displayName(underlyingFile);
  }

  public void notifyTycked(@NotNull ResolveInfo moduleResolve, @NotNull ImmutableSeq<TyckDef> tycked) {
    this.resolveInfo = moduleResolve;
    this.tycked = tycked;
    if (isLiterate) {
      literateData.resolve(moduleResolve);
      literateData.tyck(moduleResolve);
    }
  }

  public @NotNull Doc pretty(
    @NotNull ImmutableSeq<Problem> problems,
    @NotNull LiterateData.InjectedFrontMatter frontMatter,
    @NotNull PrettierOptions options
  ) throws IOException {
    return LiterateData.toDoc(this, moduleName(), program, problems, frontMatter, options);
  }

  @Override public @NotNull GenericAyaProgram parseMe(@NotNull GenericAyaParser parser) throws IOException {
    if (isLiterate) {
      var data = LiterateData.create(originalFile(), parser.reporter());
      data.parseMe(parser);
      literateData = data;
    }

    var ayaProgram = GenericAyaFile.super.parseMe(parser);
    program = ayaProgram.program();

    if (ayaProgram instanceof NodedAyaProgram nodedProgram) {
      rootNode = nodedProgram.root();
    } else {
      rootNode = null;
    }

    return ayaProgram;
  }

  @Override public @NotNull Literate literate() throws IOException {
    return isLiterate ? literateData.literate() : GenericAyaFile.super.literate();
  }

  @Override public @NotNull SourceFile codeFile() throws IOException {
    return isLiterate ? literateData.extractedAya() : GenericAyaFile.super.codeFile();
  }

  @Override public @NotNull SourceFile originalFile() throws IOException {
    return originalFile(Files.readString(underlyingFile));
  }

  public @NotNull SourceFile originalFile(@NotNull String sourceCode) {
    return new SourceFile(displayPath().toString(), underlyingFile, sourceCode);
  }

  public @NotNull Path compiledCorePath() {
    var mod = moduleName().module();
    return AyaFiles.resolveAyaCompiledFile(owner.outDir(), mod);
  }

  @Override public @NotNull String toString() { return underlyingFile.toString(); }
  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LibrarySource that = (LibrarySource) o;
    return owner.underlyingLibrary() == that.owner.underlyingLibrary() && underlyingFile.equals(that.underlyingFile);
  }

  @Override public int hashCode() {
    return Objects.hash(owner.underlyingLibrary(), underlyingFile);
  }
}

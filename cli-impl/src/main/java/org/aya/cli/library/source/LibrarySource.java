// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.source;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.value.MutableValue;
import org.aya.cli.utils.LiterateData;
import org.aya.concrete.GenericAyaFile;
import org.aya.concrete.GenericAyaParser;
import org.aya.concrete.remark.Literate;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.GenericDef;
import org.aya.generic.util.AyaFiles;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.ResolveInfo;
import org.aya.util.FileUtil;
import org.aya.util.error.SourceFile;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * A source file may or may not be tycked.
 *
 * @param program     initialized after parse
 * @param resolveInfo initialized after resolve
 * @param tycked      initialized after tyck
 */
@Debug.Renderer(text = "displayPath()")
public record LibrarySource(
  @NotNull LibraryOwner owner,
  @NotNull Path underlyingFile,
  boolean isLiterate,
  @NotNull MutableList<LibrarySource> imports,
  @NotNull MutableValue<ImmutableSeq<Stmt>> program,
  @NotNull MutableValue<ImmutableSeq<GenericDef>> tycked,
  @NotNull MutableValue<ResolveInfo> resolveInfo,
  @NotNull MutableValue<LiterateData> literateData
) implements GenericAyaFile {
  public static @NotNull LibrarySource create(@NotNull LibraryOwner owner, @NotNull Path file) {
    var underlyingFile = FileUtil.canonicalize(file);
    return new LibrarySource(owner, underlyingFile, AyaFiles.isLiterate(underlyingFile),
      MutableList.create(), MutableValue.create(),
      MutableValue.create(), MutableValue.create(), MutableValue.create());
  }

  public @NotNull ImmutableSeq<String> moduleName() {
    var info = resolveInfo.get();
    if (info != null) return info.thisModule().modulePath().path();
    var display = displayPath();
    var displayNoExt = display.resolveSibling(AyaFiles.stripAyaSourcePostfix(display.getFileName().toString()));
    return IntStream.range(0, displayNoExt.getNameCount())
      .mapToObj(i -> displayNoExt.getName(i).toString())
      .collect(ImmutableSeq.factory());
  }

  public @NotNull Path displayPath() {
    return owner.locator().displayName(underlyingFile);
  }

  public void notifyTycked(@NotNull ResolveInfo moduleResolve, @NotNull ImmutableSeq<GenericDef> tycked) {
    this.resolveInfo.set(moduleResolve);
    this.tycked.set(tycked);
    if (isLiterate) {
      var data = literateData.get();
      data.resolve(moduleResolve);
      data.tyck(moduleResolve);
    }
  }

  public @NotNull Doc pretty(@NotNull ImmutableSeq<Problem> problems, @NotNull PrettierOptions options) throws IOException {
    return LiterateData.toDoc(this, moduleName(), program.get(), problems, options);
  }

  @Override public @NotNull ImmutableSeq<Stmt> parseMe(@NotNull GenericAyaParser parser) throws IOException {
    if (isLiterate) {
      var data = LiterateData.create(originalFile(), parser.reporter());
      data.parseMe(parser);
      literateData.set(data);
    }
    var stmts = GenericAyaFile.super.parseMe(parser);
    program.set(stmts);
    return stmts;
  }

  @Override public @NotNull Literate literate() throws IOException {
    return isLiterate ? literateData.get().literate() : GenericAyaFile.super.literate();
  }

  @Override public @NotNull SourceFile codeFile() throws IOException {
    return isLiterate ? literateData.get().extractedAya() : GenericAyaFile.super.codeFile();
  }

  @Override public @NotNull SourceFile originalFile() throws IOException {
    return originalFile(Files.readString(underlyingFile));
  }

  public @NotNull SourceFile originalFile(@NotNull String sourceCode) {
    return new SourceFile(displayPath().toString(), underlyingFile, sourceCode);
  }

  public @NotNull Path compiledCorePath() {
    var mod = moduleName();
    return AyaFiles.resolveAyaCompiledFile(owner.outDir(), mod);
  }

  @Override public String toString() {
    return underlyingFile.toString();
  }

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

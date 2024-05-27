// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.cli;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.ImmutableSet;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.utils.LiteratePrettierOptions;
import org.aya.util.Version;
import org.aya.util.error.SourceFileLocator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LibraryGraphTest {
  private static final class TestLibraryOwner implements LibraryOwner {
    private final @NotNull MutableList<LibraryOwner> mutLibraryDeps;
    private final @NotNull LibraryConfig underlyingLibrary;

    public TestLibraryOwner(@NotNull LibraryConfig underlyingLibrary) {
      this.mutLibraryDeps = MutableList.create();
      this.underlyingLibrary = underlyingLibrary;
    }
    public void addDependency(@NotNull LibraryOwner dep) { mutLibraryDeps.append(dep); }
    @Override public @NotNull SeqView<Path> modulePath() { throw new UnsupportedOperationException(); }
    @Override public @NotNull SeqView<LibrarySource> librarySources() { throw new UnsupportedOperationException(); }
    @Override public @NotNull SourceFileLocator locator() { throw new UnsupportedOperationException(); }
    @Override public @NotNull SeqView<LibraryOwner> libraryDeps() { return mutLibraryDeps.view(); }
    @Override public @NotNull LibraryConfig underlyingLibrary() { return underlyingLibrary; }
    @Override public void addModulePath(@NotNull Path newPath) { throw new UnsupportedOperationException(); }
  }

  /**
   * Create a {@link LibraryConfig} for identifying.
   */
  private @NotNull LibraryConfig config(@NotNull String name) {
    var libRoot = Path.of(STR."/home/senpai/\{name}");

    return new LibraryConfig(
      Version.create("11.4.514"),
      name,
      "1.9.19",
      libRoot,
      libRoot.resolve("src"),
      libRoot.resolve("build"),
      libRoot.resolve("build/out"),
      new LibraryConfig.LibraryLiterateConfig(new LiteratePrettierOptions(), "11.4.5.14", libRoot.resolve("literate")),
      ImmutableSeq.empty()
    );
  }

  /**
   * @param libs the first element is the root library
   */
  private void check(LibraryOwner... libs) {
    var libSet = ImmutableSet.from(libs);
    var expected = MutableMap.<LibraryConfig, MutableList<LibraryConfig>>create();
    for (LibraryOwner lib : libs) {
      expected.put(lib.underlyingLibrary(), MutableList.from(lib.libraryDeps().map(LibraryOwner::underlyingLibrary)));
    }

    assertEquals(libSet, LibraryOwner.collectDependencies(libs[0]));
    assertEquals(expected, LibraryOwner.buildDependencyGraph(libs[0]).E());
    assertEquals(expected, LibraryOwner.buildDependencyGraph(libSet.view()).E());
  }

  @Test public void libGraph0() {
    // A -> B -> C
    //      ^____|
    var a = new TestLibraryOwner(config("A"));
    var b1 = new TestLibraryOwner(config("B"));
    var b2 = new TestLibraryOwner(b1.underlyingLibrary());
    var c = new TestLibraryOwner(config("C"));

    a.addDependency(b1);
    b1.addDependency(c);
    c.addDependency(b2);
    b2.addDependency(c);

    check(a, b1, c, b2);
  }

  @Test public void libGraph1() {
    // A -> B -> C
    // ^____^____|

    var a1 = new TestLibraryOwner(config("A"));
    var a2 = new TestLibraryOwner(a1.underlyingLibrary());
    var b1 = new TestLibraryOwner(config("B"));
    var b2 = new TestLibraryOwner(b1.underlyingLibrary());
    var c = new TestLibraryOwner(config("C"));

    a1.addDependency(b1);
    b1.addDependency(c);
    c.addDependency(b2);
    b2.addDependency(c);
    c.addDependency(a2);
    a2.addDependency(b2);

    check(a1, b1, c, a2, b2);
  }
}

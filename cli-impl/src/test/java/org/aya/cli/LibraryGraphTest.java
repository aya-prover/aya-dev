// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSet;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.util.error.SourceFileLocator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LibraryGraphTest {
  private static final class TestLibraryOwner implements LibraryOwner {
    public final @NotNull MutableList<LibraryOwner> mutLibraryDeps;

    public TestLibraryOwner() {
      this.mutLibraryDeps = MutableList.create();
    }

    @Override
    public @NotNull SeqView<Path> modulePath() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull SeqView<LibrarySource> librarySources() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull SourceFileLocator locator() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull SeqView<LibraryOwner> libraryDeps() {
      return mutLibraryDeps.view();
    }

    @Override
    public @NotNull LibraryConfig underlyingLibrary() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addModulePath(@NotNull Path newPath) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * @param libs the first element is the root library
   */
  private void check(LibraryOwner... libs) {
    var libSet = ImmutableSet.from(libs);
    var expected = MutableMap.<LibraryOwner, MutableList<LibraryOwner>>create();
    libSet.forEach(lib ->
      expected.put(lib, MutableList.from(lib.libraryDeps()))
    );

    assertEquals(libSet, LibraryOwner.collectDependencies(libs[0]));
    assertEquals(expected, LibraryOwner.buildDependencyGraph(libs[0]).E());
    assertEquals(expected, LibraryOwner.buildDependencyGraph(libSet.view()).E());
  }


  @Test
  public void libGraph0() {
    // A -> B -> C
    //      ^____|
    var a = new TestLibraryOwner();
    var b = new TestLibraryOwner();
    var c = new TestLibraryOwner();

    a.mutLibraryDeps.append(b);
    b.mutLibraryDeps.append(c);
    c.mutLibraryDeps.append(b);

    check(a, b, c);
  }

  @Test
  public void libGraph1() {
    // A -> B -> C
    // ^____^____|

    var a = new TestLibraryOwner();
    var b = new TestLibraryOwner();
    var c = new TestLibraryOwner();

    a.mutLibraryDeps.append(b);
    b.mutLibraryDeps.append(c);
    c.mutLibraryDeps.append(a);
    c.mutLibraryDeps.append(b);

    check(a, b, c);
  }
}

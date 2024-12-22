// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.json;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public sealed interface LibraryDependency {
  @NotNull String depName();

  record DepVersion(@Override @NotNull String depName, @NotNull String version) implements LibraryDependency { }
  record DepGithub(@Override @NotNull String depName, @NotNull String repo) implements LibraryDependency { }
  record DepFile(@Override @NotNull String depName, @NotNull Path depRoot) implements LibraryDependency { }
}

// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;

public interface AsmOutputCollector {
  void write(@NotNull ClassDesc className, byte @NotNull [] bytecode);

  record Default(@NotNull MutableMap<Path, byte[]> output) implements AsmOutputCollector {
    public Default() { this(MutableMap.create()); }

    public static @NotNull Path from(@NotNull ImmutableSeq<String> components) {
      return Path.of(components.getFirst(), components.view().drop(1).toArray(String[]::new));
    }

    public static @NotNull Path getPath(@NotNull ClassDesc className) {
      var str = className.descriptorString();
      var components = ImmutableSeq.from(str.substring(1, str.length() - 1).split("/"));
      var fileName = components.getLast() + ".class";
      return from(components.view().dropLast(1).appended(fileName).toImmutableSeq());
    }

    @Override public void write(@NotNull ClassDesc className, byte @NotNull [] bytecode) {
      var filePath = getPath(className);
      output.put(filePath, bytecode);
    }

    public void writeTo(@NotNull Path baseDir) throws IOException {
      output.forEachChecked(((path, bytes) -> {
        var filePath = baseDir.resolve(path);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, bytes);
      }));
    }
  }
}

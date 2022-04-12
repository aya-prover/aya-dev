// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.gradle;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class StripPreviewRunner {
  public static void run(File tempDir, Set<File> jar) throws IOException {
    var tempPath = tempDir.toPath().resolve("stripping");
    deleteRecursively(tempPath);
    Files.createDirectories(tempPath);
    jar.stream().map(i -> i.toPath()).forEach(j -> process(tempPath, j));
  }

  public static void process(Path tempDir, Path jar) {
    try {
      unpack(tempDir, jar);
      Files.walk(tempDir).filter(p -> !Files.isDirectory(p))
        .filter(p -> p.getFileName().toString().endsWith(".class"))
        .forEach(p -> stripPreview(p));
      var outputJar = jar.resolveSibling(jar.getFileName().toString().replace(".jar", "-no-preview.jar"));
      pack(outputJar, List.of(tempDir));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void stripPreview(Path classFile) {
    // struct Head {
    //   u4 magic;
    //   u2 minor_version;
    //   u2 major_version;
    // };
    final int careSize = 4 + 2 + 2;
    try (var raf = new RandomAccessFile(classFile.toFile(), "rw")) {
      var mm = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, careSize);
      int magic = mm.getInt();
      int minor = mm.getShort(4) & 0xFFFF;
      if (magic == 0xCAFEBABE && minor == 0xFFFF) {
        System.out.printf("AyaStripPreview: %s has preview feature bit set (minor = %d), clearing\n", classFile.toAbsolutePath(), minor);
        mm.putShort(4, (short) 0);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void unpack(Path outputDir, Path zip) throws IOException {
    try (var zipFile = new ZipFile(zip.toFile())) {
      var entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        var entryOut = outputDir.resolve(entry.getName());
        if (entry.isDirectory()) {
          Files.createDirectories(entryOut);
        } else {
          Files.createDirectories(entryOut.getParent());
          try (var in = zipFile.getInputStream(entry)) {
            Files.copy(in, entryOut);
          }
        }
      }
    }
  }

  public static void pack(Path outputPluginJar, List<Path> sources) throws IOException {
    Files.deleteIfExists(outputPluginJar);
    var zip = Files.createFile(outputPluginJar);
    try (var zipStream = new ZipOutputStream(Files.newOutputStream(zip))) {
      for (var dir : sources) {
        Files.walk(dir)
          .filter(path -> !Files.isDirectory(path))
          .forEach(path -> {
            try {
              var entry = new ZipEntry(dir.relativize(path).toString());
              zipStream.putNextEntry(entry);
              Files.copy(path, zipStream);
              zipStream.closeEntry();
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
      }
    }
  }

  static void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) return;
    try (var walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder())
        .forEach(i -> {
          try {
            Files.deleteIfExists(i);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
    }
  }
}

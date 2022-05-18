// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.gradle;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class StripPreview {
  public static void stripPreview(Path root, Path classFile, boolean forceJava17) {
    // struct Head {
    //   u4 magic;
    //   u2 minor_version;
    //   u2 major_version;
    // };
    final int careSize = 4 + 2 + 2;
    try (var raf = new RandomAccessFile(classFile.toFile(), "rw")) {
      var mm = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, careSize);
      int magic = mm.getInt(0);
      int minor = mm.getShort(4) & 0xFFFF;
      int major = mm.getShort(6) & 0xFFFF;
      if (magic != 0xCAFEBABE) return;
      var relative = root.relativize(classFile.toAbsolutePath());
      if (minor == 0xFFFF) {
        mm.putShort(4, (short) 0);
        System.out.printf("AyaStripPreview: %s has preview bit (minor = %d), cleared\n", relative, minor);
      }
      // Java 17 uses major version 61
      if (forceJava17 && major > 61) {
        System.out.printf("AyaStripPreview: %s has major version %d, forcing to 61 (Java 17)\n", relative, major);
        mm.putShort(6, (short) 61);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

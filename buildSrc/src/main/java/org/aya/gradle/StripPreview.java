// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.gradle;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class StripPreview {
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
}

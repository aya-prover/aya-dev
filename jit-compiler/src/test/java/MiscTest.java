// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.compiler.AyaSerializer;
import org.aya.compiler.NameSerializer;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.ref.QName;
import org.aya.syntax.ref.QPath;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MiscTest {
  public static final @NotNull QPath TOP = new QPath(ModulePath.of("baka", "114514"), 2);
  public static final @NotNull QPath SUB = TOP.derive("hentai");
  public static final @NotNull QName NAME = new QName(SUB, "urusai");

  @Test public void test0() {
    assertEquals(AyaSerializer.PACKAGE_BASE + ".baka.$114514",
      NameSerializer.getClassReference(TOP, null));
    assertEquals(AyaSerializer.PACKAGE_BASE + ".baka.$114514.$114514$shinji",
      NameSerializer.getClassReference(TOP, "shinji"));
    assertEquals(AyaSerializer.PACKAGE_BASE + ".baka.$114514",
      NameSerializer.getClassReference(SUB, null));
    assertEquals(AyaSerializer.PACKAGE_BASE + ".baka.$114514.$114514$hentai$shinji",
      NameSerializer.getClassReference(SUB, "shinji"));
    assertEquals(AyaSerializer.PACKAGE_BASE + ".baka.$114514.$114514$hentai$urusai",
      NameSerializer.getClassReference(NAME.module(), NAME.name()));
  }
}

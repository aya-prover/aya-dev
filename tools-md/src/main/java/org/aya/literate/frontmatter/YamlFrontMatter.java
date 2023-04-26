// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate.frontmatter;

import org.aya.literate.FencedBlock;
import org.jetbrains.annotations.NotNull;

public class YamlFrontMatter extends FencedBlock {
  public static final @NotNull Factory<YamlFrontMatter> FACTORY = new Factory<>(YamlFrontMatter::new, '-', 3);
}

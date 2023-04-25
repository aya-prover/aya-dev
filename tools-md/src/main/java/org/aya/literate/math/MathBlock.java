// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate.math;

import org.aya.literate.FencedBlock;
import org.jetbrains.annotations.NotNull;

/**
 * @see org.commonmark.node.FencedCodeBlock
 */
public class MathBlock extends FencedBlock {
  public static final @NotNull Factory<MathBlock> FACTORY = new Factory<>(MathBlock::new, '$', 2);
}

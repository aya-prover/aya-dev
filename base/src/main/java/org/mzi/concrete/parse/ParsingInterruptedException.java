// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.concrete.parse;

import org.mzi.api.util.MziInterruptException;

/**
 * @author kiva
 */
public class ParsingInterruptedException extends MziInterruptException {
  @Override public InterruptStage stage() {
    return InterruptStage.Parsing;
  }
}

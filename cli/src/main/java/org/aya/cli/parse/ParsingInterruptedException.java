// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import org.aya.generic.util.InterruptException;

/**
 * @author kiva
 */
public class ParsingInterruptedException extends InterruptException {
  @Override public InterruptStage stage() {
    return InterruptStage.Parsing;
  }
}

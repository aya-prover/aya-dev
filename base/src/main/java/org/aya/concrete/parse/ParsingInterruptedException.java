// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.parse;

import org.aya.api.util.InterruptException;

/**
 * @author kiva
 */
public class ParsingInterruptedException extends InterruptException {
  @Override public InterruptStage stage() {
    return InterruptStage.Parsing;
  }
}

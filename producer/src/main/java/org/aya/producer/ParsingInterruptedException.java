// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.producer;

import org.aya.generic.InterruptException;

/**
 * @author kiva
 */
public class ParsingInterruptedException extends InterruptException {
  @Override public InterruptStage stage() { return InterruptStage.Parsing; }
}

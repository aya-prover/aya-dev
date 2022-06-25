// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.tuple.Unit;
import org.aya.core.term.Tm;

/**
 * A `TerminalFolder` always yields a dummy value as the result of traversal.
 * This interface is more useful when used as an escape hatch:
 * The implementing class can hold and interact with arbitrary states during traversal.
 *
 * @author wsx
 */
public interface TerminalFolder extends Folder<Unit> {
  @Override default Unit fold(Tm<Unit> tm) {
    return Unit.unit();
  }
}

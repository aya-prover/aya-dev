// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

public final class IntervalTerm implements StableWHNF, Formation {
  public static final IntervalTerm INSTANCE = new IntervalTerm();

  private IntervalTerm() {

  }
}

// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

public enum State {
  // like trying to split a non-constructor (fail negatively)
  Stuck,
  // like trying to match zero with suc (fail positively)
  Mismatch
}

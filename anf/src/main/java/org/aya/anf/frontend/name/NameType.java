// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend.name;

/// Denotes the type of name that a given `ProposedName` refers to, in decreasing priority.
public enum NameType {

  SingleSpecificUseCase,
  SingleGenericUseCase,
  ReductionResult,
}

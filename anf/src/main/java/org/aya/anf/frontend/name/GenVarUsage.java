// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend.name;

/// `GenVarUsage` represents how a generated variable is being used in the IR. A generated
/// variable/binding can have multiple uses, thus having multiple `GenVarUsage`s. As for now
/// this is mainly used for readable variable name generation.
public sealed interface GenVarUsage permits GenVarUsage.RedexParam {

  record RedexParam() implements GenVarUsage {}

}

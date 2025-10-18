// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

public sealed interface AstVariable {
  record Local(int index) implements AstVariable {
  }
  record Arg(int nth) implements AstVariable {
  }
  record Capture(int nth) implements AstVariable {
  }
}

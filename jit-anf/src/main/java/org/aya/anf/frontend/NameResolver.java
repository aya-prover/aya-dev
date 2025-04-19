// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend;

import kala.collection.mutable.MutableStack;

/// This class manages the creation of variable/binding names when lowering
/// from core syntax into ANF.
public class NameResolver {

  private final MutableStack<Scope> scopes = MutableStack.create();

}

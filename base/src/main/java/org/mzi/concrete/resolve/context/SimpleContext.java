// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.context;

import asia.kala.collection.mutable.MutableHashMap;
import asia.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;

/**
 * @author re-xyr
 */
public final class SimpleContext implements Context {
  private final MutableMap<String, Var> variables = new MutableHashMap<>();
  private final MutableMap<String, Context> subContexts = new MutableHashMap<>();
  private Context superContext;

  @Override public @Nullable Var getLocal(String name) {
    return variables.get(name);
  }

  @Override public boolean containsLocal(String name) {
    return variables.containsKey(name);
  }

  @Override public void unsafePutLocal(String name, Var ref) {
    variables.put(name, ref);
  }

  @Override public boolean containsSubContextLocal(String name) {
    return subContexts.containsKey(name);
  }

  @Override public @Nullable Context getSubContextLocal(String name) {
    return subContexts.get(name);
  }

  @Override public void unsafePutSubContextLocal(String name, Context ctx) {
    subContexts.put(name, ctx);
  }

  @Override public @Nullable Context getSuperContext() {
    return superContext;
  }

  @Override public void setSuperContext(Context ctx) {
    superContext = ctx;
  }
}

// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.context;

import asia.kala.Tuple2;
import asia.kala.collection.mutable.MutableHashMap;
import asia.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;

/**
 * @author re-xyr
 */
public final class SimpleContext implements Context {
  private final MutableMap<String, Tuple2<Var, Boolean>> variables = new MutableHashMap<>(); // (variable, isPublic)
  private final MutableMap<String, Tuple2<Context, Boolean>> subContexts = new MutableHashMap<>(); // (context, isPublic)
  private Context superContext;

  @Override public @Nullable Var getLocal(String name, boolean withPrivate) {
    var variable = variables.get(name);
    if (variable == null || !(variable._2 || withPrivate)) return null;
    return variable._1;
  }

  @Override public boolean containsLocal(String name) {
    return variables.containsKey(name);
  }

  @Override public void unsafePutLocal(String name, Var ref, boolean isPublic) {
    variables.put(name, Tuple2.of(ref, isPublic));
  }

  @Override public boolean containsSubContextLocal(String name) {
    return subContexts.containsKey(name);
  }

  @Override public @Nullable Context getSubContextLocal(String name, boolean withPrivate) {
    var ctx = subContexts.get(name);
    if (ctx == null || !(ctx._2 || withPrivate)) return null;
    return ctx._1;
  }

  @Override public void unsafePutSubContextLocal(String name, Context ctx, boolean isPublic) {
    subContexts.put(name, Tuple2.of(ctx, isPublic));
  }

  @Override public @Nullable Context getSuperContext() {
    return superContext;
  }

  @Override public void setSuperContext(Context ctx) {
    superContext = ctx;
  }
}

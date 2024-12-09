// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeJava;
import org.aya.compiler.free.FreeJavaBuilder;
import org.aya.compiler.free.FreeUtils;
import org.aya.compiler.free.data.MethodData;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public abstract class AbstractExprializer<T> {
  protected final @NotNull FreeJavaBuilder.ExprBuilder builder;

  protected AbstractExprializer(@NotNull FreeJavaBuilder.ExprBuilder builder) { this.builder = builder; }

  public final @NotNull FreeJava makeImmutableSeq(@NotNull Class<?> typeName, @NotNull ImmutableSeq<FreeJava> terms) {
    return makeImmutableSeq(Constants.IMMSEQ, typeName, terms);
  }

  public final @NotNull FreeJava makeImmutableSeq(@NotNull MethodData con, @NotNull Class<?> typeName, @NotNull ImmutableSeq<FreeJava> terms) {
    var args = builder.mkArray(FreeUtils.fromClass(typeName), terms.size(), terms);
    return builder.invoke(con, ImmutableSeq.of(args));
  }

  public final @NotNull FreeJava serializeToImmutableSeq(@NotNull Class<?> typeName, @NotNull ImmutableSeq<T> terms) {
    return makeImmutableSeq(typeName, terms.map(this::doSerialize));
  }

  public final @NotNull FreeJava getInstance(@NotNull ClassDesc def) {
    return builder.refField(builder.resolver().resolve(def, AyaSerializer.STATIC_FIELD_INSTANCE, def));
  }

  /**
   * Return the reference to the {@code INSTANCE} field of the compiled class to {@param def}
   */
  public final @NotNull FreeJava getInstance(@NotNull AnyDef def) {
    return getInstance(NameSerializer.getClassDesc(def));
  }

  public final @NotNull FreeJava getRef(@NotNull CallKind callType, @NotNull FreeJava call) {
    return builder.refField(builder.resolver().resolve(callType.callType, AyaSerializer.FIELD_INSTANCE, callType.refType), call);
  }

  public final @NotNull FreeJava getCallInstance(@NotNull CallKind callType, @NotNull AnyDef def) {
    return builder.refField(builder.resolver().resolve(
      NameSerializer.getClassDesc(def),
      AyaSerializer.FIELD_EMPTYCALL,
      callType.callType)
    );
  }

  protected abstract @NotNull FreeJava doSerialize(@NotNull T term);

  public abstract @NotNull FreeJava serialize(T unit);
}

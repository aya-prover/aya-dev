// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

public record MethodRef(
  @Override @NotNull ClassDesc owner,
  @Override @NotNull String name,
  @Override @NotNull ClassDesc returnType,
  @Override @NotNull ImmutableSeq<ClassDesc> paramTypes,
  @Override boolean isInterface
) implements Docile {
  public boolean isConstructor() {
    return name().equals(ConstantDescs.INIT_NAME);
  }

  public boolean checkArguments(@NotNull ImmutableSeq<?> args) {
    return paramTypes.sizeEquals(args);
  }

  @Override public @NotNull Doc toDoc() {
    return Doc.cat(Doc.plain(owner.displayName()), Doc.symbol("."), Doc.plain(name), Doc.parened(
      Doc.commaList(paramTypes.view().map(ClassDesc::displayName).map(Doc::plain))
    ), Doc.spaced(Doc.symbol("->")), Doc.plain(returnType.displayName()));
  }
}

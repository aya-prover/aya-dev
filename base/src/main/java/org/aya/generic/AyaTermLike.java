// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.aya.guest0x0.cubical.Restr;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public interface AyaTermLike<E extends AyaTermLike<E>> extends Restr.TermLike<E>, AyaDocile {
  default @Override @NotNull Doc toDoc() {
    //noinspection deprecation
    return debuggerOnlyToDoc();
  }
}

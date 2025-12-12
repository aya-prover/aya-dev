// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.ser;

import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public record SerQualifiedID(@NotNull ModuleName component, @NotNull String name) implements Serializable {
  public static @NotNull SerQualifiedID from(@NotNull QualifiedID qid) {
    return new SerQualifiedID(qid.component(), qid.name());
  }
  public @NotNull QualifiedID make() { return new QualifiedID(SourcePos.SER, component, name); }
}

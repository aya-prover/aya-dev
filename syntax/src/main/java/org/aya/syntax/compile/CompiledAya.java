// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import org.aya.syntax.core.repr.CodeShape;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface CompiledAya {
  String NAME_MODULE = "module";
  String NAME_FILE_MODULE_SIZE = "fileModuleSize";
  String NAME_NAME = "name";
  String NAME_ASSOC = "assoc";
  String NAME_SHAPE = "shape";
  String NAME_RECOGNITION = "recognition";

  @NotNull String[] module();
  int fileModuleSize();
  @NotNull String name();
  /** @return the index in the Assoc enum, -1 if null */
  int assoc() default -1;
  /** @return the index in the AyaShape enum, -1 if null */
  int shape() default -1;
  @NotNull CodeShape.GlobalId[] recognition() default { };
}

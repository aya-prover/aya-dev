// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import org.aya.syntax.core.repr.CodeShape;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface AyaMetadata {
  @NonNls String NAME_MODULE = "module";
  @NonNls String NAME_FILE_MODULE_SIZE = "fileModuleSize";
  @NonNls String NAME_NAME = "name";
  @NonNls String NAME_ASSOC = "assoc";
  @NonNls String NAME_SHAPE = "shape";
  @NonNls String NAME_RECOGNITION = "recognition";
  @NonNls String INSTANCE_OF_CLASS = "instanceOfClass";

  @NotNull String[] module();
  int fileModuleSize();
  @NotNull String name();
  /// @return the index in the Assoc enum, -1 if null
  int assoc() default -1;
  /// @return the index in the AyaShape enum, -1 if null
  int shape() default -1;
  @NotNull CodeShape.GlobalId[] recognition() default { };
  @NotNull Class<?> instanceOfClass() default Object.class;
}

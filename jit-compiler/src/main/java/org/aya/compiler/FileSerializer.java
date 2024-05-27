// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import org.aya.generic.NameGenerator;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.ref.ModulePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileSerializer extends AbstractSerializer<FileSerializer.FileResult> {
  public record FileResult(
    @Nullable ModulePath parentModule,
    @NotNull ModuleSerializer.ModuleResult moduleResult
  ) { }

  private final @NotNull ShapeFactory shapeFactory;

  public FileSerializer(@NotNull ShapeFactory factory) {
    super(new StringBuilder(), 0, new NameGenerator());
    this.shapeFactory = factory;
  }

  private void buildPackage(@Nullable ModulePath path) {
    appendLine(STR."package \{getModuleReference(path)};");
  }

  @Override
  public AyaSerializer<FileResult> serialize(FileResult unit) {
    buildPackage(unit.parentModule);
    appendLine();
    appendLine(AyaSerializer.IMPORT_BLOCK);
    appendLine();

    new ModuleSerializer(this, shapeFactory)
      .serialize(unit.moduleResult);

    return this;
  }
}

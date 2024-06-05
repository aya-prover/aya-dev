// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import org.aya.primitive.ShapeFactory;
import org.aya.syntax.ref.ModulePath;
import org.jetbrains.annotations.NotNull;

import static org.aya.compiler.NameSerializer.PACKAGE_SEPARATOR;
import static org.aya.compiler.NameSerializer.getModulePackageReference;

public class FileSerializer extends AbstractSerializer<ModuleSerializer.ModuleResult> {
  private final @NotNull ShapeFactory shapeFactory;

  public FileSerializer(@NotNull ShapeFactory factory) {
    super(new SourceBuilder.Default());
    this.shapeFactory = factory;
  }

  private void buildPackage(@NotNull ModulePath path) {
    appendLine(STR."package \{getModulePackageReference(path, PACKAGE_SEPARATOR)};");
  }

  @Override public @NotNull FileSerializer serialize(ModuleSerializer.ModuleResult unit) {
    assert unit.name().isFileModule();
    buildPackage(unit.name().module());
    appendLine();
    appendLine(AyaSerializer.IMPORT_BLOCK);
    appendLine();

    new ModuleSerializer(this, shapeFactory)
      .serialize(unit);

    return this;
  }
}

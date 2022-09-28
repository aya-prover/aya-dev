package org.aya.parser.ij;

import com.intellij.psi.FleetPsiParser;
import com.intellij.psi.builder.FleetPsiBuilder;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;

public class AyaFleetParser extends FleetPsiParser.DefaultPsiParser {
  public AyaFleetParser() {
    super(new AyaFleetParserDefinition());
  }

  private static final class AyaFleetParserDefinition extends AyaParserDefinitionBase {
    private final @NotNull IFileElementType FILE = new IFileElementType(AyaLanguage.INSTANCE) {
      @Override public void parse(@NotNull FleetPsiBuilder<?> builder) {
      }
    };

    @Override public @NotNull IFileElementType getFileNodeType() {
      return FILE;
    }
  }
}

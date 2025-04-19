// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.anf.ANFModule;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TestPipeline {

  @Test
  public void simpleNat() throws IOException {
    var stream = TestPipeline.class.getResourceAsStream("/SimpleNat.aya");
    assert stream != null;
    @Language("Aya") var code = new String(stream.readAllBytes());
    stream.close();
    var result = TestUtils.tyck(code);
    var module = ANFModule.from(result.defs().view());
    System.out.println(module.debugRender());
  }
}

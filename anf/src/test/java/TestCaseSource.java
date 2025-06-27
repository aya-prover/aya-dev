// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public sealed interface TestCaseSource permits TestCaseSource.TestFile, TestCaseSource.NoFile {

  @NotNull String readableSource();
  @NotNull String code() throws IOException;

  record TestFile(@NotNull String path) implements TestCaseSource {
    @Override
    public @NotNull String readableSource() { return path; }
    @Override
    public @NotNull String code() throws IOException {
      var stream = TestCaseSource.class.getResourceAsStream(path);
      assert stream != null;
      var code = new String(stream.readAllBytes());
      stream.close();
      return code;
    }
  }

  record NoFile(@NotNull String code) implements TestCaseSource {
    @Override
    public @NotNull String readableSource() { return "<no source>"; }
  }
}

// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.language;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PublishSyntaxHighlightParams {
  private @NotNull String uri;

  public PublishSyntaxHighlightParams(@NotNull String uri) {
    this.uri = uri;
  }

  public @NotNull String getUri() {
    return uri;
  }

  public void setUri(@NotNull String uri) {
    this.uri = uri;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PublishSyntaxHighlightParams that = (PublishSyntaxHighlightParams) o;
    return uri.equals(that.uri);
  }

  @Override public int hashCode() {
    return Objects.hash(uri);
  }
}

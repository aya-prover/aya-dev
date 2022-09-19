// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.plct;

import com.google.gson.annotations.SerializedName;

import java.time.LocalDateTime;

public interface GsonClasses {
  final class Milestone {
    String title;
    @SerializedName("html_url")
    String url;
  }

  final class User {
    String login;
    String url;
  }

  final class PR {
    int number;
    String title;
    @SerializedName("html_url")
    String url;
    @SerializedName("updated_at")
    LocalDateTime updatedAt;
    User user;
    Milestone milestone;
  }
}

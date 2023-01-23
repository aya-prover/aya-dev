// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.plct;

import com.google.gson.annotations.SerializedName;

import java.time.LocalDateTime;

public interface GsonClasses {
  final class Milestone {
    public String title;
    @SerializedName("html_url")
    public String url;
  }

  final class User {
    public String login;
    public String url;
  }

  final class PR {
    public int number;
    public String title;
    @SerializedName("html_url")
    public String url;
    @SerializedName("updated_at")
    public LocalDateTime updatedAt;
    public User user;
    public Milestone milestone;
  }
}

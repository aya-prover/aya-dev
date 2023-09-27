// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.gradle;

import org.jetbrains.annotations.NotNull;

// TODO: move to build utils
public record JdkUrls(int javaVersion, String platform) {
  public @NotNull String libericaJDK() {
    var libericaJdkVersion = System.getProperty("java.vm.version");

    var fixAmd64 = platform.replace("x64", "amd64");
    var suffix = platform.contains("linux") ? "tar.gz" : "zip";
    // "https://download.bell-sw.com/java/${libericaJdkVersion}/bellsoft-jdk${libericaJdkVersion}-${fixAmd64}.$suffix"
    return "https://download.bell-sw.com/java/" + libericaJdkVersion + "/bellsoft-jdk"
      + libericaJdkVersion + '-' + fixAmd64 + '.' + suffix;
  }

  public @NotNull String riscv64JDK() {
    return "https://github.com/imkiva/openjdk-riscv-build/releases/download/bootstrap/openjdk-jdk" + javaVersion + "-" + platform + ".tar.gz";
  }

  public @NotNull String jdk() {
    if (platform.contains("linux") && platform.contains("riscv")) return riscv64JDK();
    return libericaJDK();
  }
}

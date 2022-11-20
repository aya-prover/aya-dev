// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.plct.PLCTReport;
import org.aya.cli.utils.MainArgs;
import org.junit.jupiter.api.Test;

public class PlctReportTest {
  @Test public void generateReport() throws Exception {
    if ("true".equals(System.getenv("CI"))) {
      var args = new MainArgs.PlctAction();
      args.plctReport = true;
      System.out.println(new PLCTReport().run(args));
    }
  }
}

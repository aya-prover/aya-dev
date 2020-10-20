package org.mzi.gradle

import org.gradle.api.tasks.Copy

class PreprocessZhihuTask extends Copy {
  {
    group = "build"
    filter { String line ->
      line
        .replaceAll($/\$\$(.*?)\$\$$/$,
          "\n<img src=\"https://www.zhihu.com/equation?tex=\$1\\\\\" alt=\"\$1\\\\\" class=\"ee_img tr_noresize\" eeimg=\"1\">\n")
        .replaceAll($/\$(.*?)\$$/$,
          "\n<img src=\"https://www.zhihu.com/equation?tex=\$1\" alt=\"\$1\" class=\"ee_img tr_noresize\" eeimg=\"1\">\n")
    }
  }
}

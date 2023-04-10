/*
 * Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
 */

document.addEventListener("DOMContentLoaded", function () {
  let blocks = document.getElementsByClassName('doc-katex-input');
  for (let i = 0; i < blocks.length; i++) {
    let block = blocks[i];
    renderMathInElement(block, {
      throwOnError: false
    });
  }
});

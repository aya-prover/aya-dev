/*
 * Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
 */

function highlight(on) {
  return function () {
    let links = document.getElementsByTagName('a');
    for (let i = 0; i < links.length; i++) {
      let that = links[i];
      if (this.href !== that.href) continue;
      if (on) that.classList.add("hover-highlight");
      else that.classList.remove("hover-highlight");
    }
  }
}

function setupHighlight(dom) {
  let links = dom.getElementsByTagName('a');
  for (let i = 0; i < links.length; i++) {
    let link = links[i];
    if (!link.hasAttribute("href")) continue;
    link.onmouseover = highlight(true);
    link.onmouseout = highlight(false);
  }
}

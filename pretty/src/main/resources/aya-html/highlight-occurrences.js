/*
 * Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
 */

let links = document.getElementsByTagName('a');
for (let i = 0; i < links.length; i++) {
  let link = links[i];
  if (!link.hasAttribute("href")) continue;
  link.onmouseover = highlight(true);
  link.onmouseout = highlight(false);
}

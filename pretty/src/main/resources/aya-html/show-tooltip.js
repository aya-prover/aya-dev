/*
 * Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
 */

let links = document.getElementsByClassName('aya-tooltip');
for (let i = 0; i < links.length; i++) {
  let link = links[i];
  if (!link.hasAttribute("data-tooltip-text")) continue;
  link.onmouseover = showTooltip(true);
  link.onmouseout = showTooltip(false);
}

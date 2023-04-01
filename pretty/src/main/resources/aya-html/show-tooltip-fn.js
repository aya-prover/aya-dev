/*
 * Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
 */

let currentHover = null;

// https://github.com/plt-amy/1lab/blob/5e5a22abce8a5cfb62b5f815e1231c1e34bb0a12/support/web/js/highlight-hover.ts#L22
function showTooltip(on) {
  return function () {
    let link = this;
    const text = link.getAttribute("data-tooltip-text");
    if (text) {
      if (currentHover) {
        currentHover.remove();
        currentHover = null;
      }

      if (on) {
        currentHover = document.createElement("div");
        currentHover.innerHTML = atob(text);
        currentHover.classList.add("AyaTooltipPopup");
        document.body.appendChild(currentHover);

        const selfRect = link.getBoundingClientRect();
        const hoverRect = currentHover.getBoundingClientRect();
        // If we're close to the bottom of the page, push the tooltip above instead.
        // The constant here is arbitrary, because trying to convert em to px in JS is a fool's errand.
        if (selfRect.bottom + hoverRect.height + 30 > window.innerHeight) {
          // 2em from the material mixin. I'm sorry
          currentHover.style.top = `calc(${link.offsetTop - hoverRect.height + 5}px - 2em)`;
        } else {
          currentHover.style.top = `${link.offsetTop + link.offsetHeight + 5}px`;
        }
        currentHover.style.left = `${link.offsetLeft}px`;
      }
    }
  }
}

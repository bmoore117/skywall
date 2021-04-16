let mm = window.matchMedia('(prefers-color-scheme: dark)');
function apply() {
    document.documentElement.setAttribute("theme", mm.matches ? "dark" : "");
    if (!mm.matches) {
        let header = document.getElementById("header-bar")
        if (header != null) {
            header.setAttribute("theme", "dark");
        }
    } else {
        let header = document.getElementById("header-bar")
        if (header != null) {
            header.removeAttribute("theme", "dark");
        }
    }
}
mm.addListener(apply);
apply();
var interval = setInterval(handleInitialRender, 500); // 2000 ms = start after 2sec
function handleInitialRender() {
    if (!mm.matches) {
        let header = document.getElementById("header-bar")
            if (header != null) {
                header.setAttribute("theme", "dark");
            }
    } else {
        let header = document.getElementById("header-bar")
        if (header != null) {
            header.removeAttribute("theme", "dark");
        }
   }
   clearInterval(interval);
}
let mm = window.matchMedia('(prefers-color-scheme: dark)');
function apply() {
    document.documentElement.setAttribute("theme",mm.matches?"dark":"");
}
mm.addListener(apply);
apply();
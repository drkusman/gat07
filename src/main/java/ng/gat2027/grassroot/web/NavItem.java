package ng.gat2027.grassroot.web;

/** Sidebar navigation entry for the dashboard shell. */
public record NavItem(String href, String label, String icon, String count, boolean exact, String section, boolean external) {
    public static NavItem of(String href, String label, String icon, String section) { return new NavItem(href, label, icon, null, false, section, false); }
    public static NavItem exact(String href, String label, String icon, String section) { return new NavItem(href, label, icon, null, true, section, false); }
    public NavItem withCount(long n) { return new NavItem(href, label, icon, String.format("%,d", n), exact, section, external); }
    public NavItem asExternal() { return new NavItem(href, label, icon, count, exact, section, true); }

    public boolean isActive(String path) {
        if (path == null) return false;
        return exact ? path.equals(href) : path.equals(href) || path.startsWith(href + "/");
    }
}

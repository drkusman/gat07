import { APP_BASE } from "@/lib/api";

const navLinkClass = (isActive: boolean) =>
  `px-3 py-2 rounded-lg font-medium hover:bg-green-light hover:text-green-dark ${isActive ? "bg-green-light text-green-dark" : "text-ink"}`;

export default function SiteHeader({ active }: { active: "/" | "/events" }) {
  return (
    <>
      <div className="h-1.5 w-full" style={{ background: "linear-gradient(90deg, var(--green) 0 33%, #fff 33% 66%, var(--red) 66%)" }} />
      <header className="bg-white border-b border-line sticky top-0 z-50">
        <div className="max-w-6xl mx-auto px-4 py-3 flex items-center gap-4">
          <a href="/" className="flex items-center gap-3">
            <img src={`${APP_BASE}/img/logo.jpg`} alt="GAT 2027" className="h-12 w-auto rounded" />
            <div>
              <div className="font-bold text-ink leading-tight">Grassroot Advocacy for Tinubu</div>
              <div className="text-xs text-muted">GAT 2027 · Forward Together with PBAT</div>
            </div>
          </a>
          <nav className="ml-auto flex gap-1">
            <a href="/events" className={navLinkClass(active === "/events")}>
              Events
            </a>
            <a
              href={`${APP_BASE}/docs/GAT-2027-Brochure.pdf`}
              target="_blank"
              rel="noopener noreferrer"
              className={navLinkClass(false)}
            >
              Brochure
            </a>
            <a href={`${APP_BASE}/login`} className={navLinkClass(false)}>
              Log in
            </a>
          </nav>
        </div>
      </header>
    </>
  );
}

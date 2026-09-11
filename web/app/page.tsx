import { APP_BASE } from "@/lib/api";
import SiteHeader from "@/components/SiteHeader";
import SiteFooter from "@/components/SiteFooter";

export default function Home() {
  return (
    <>
      <SiteHeader active="/" />

      <section className="bg-green-light">
        <div className="max-w-6xl mx-auto px-4 py-16 grid md:grid-cols-2 gap-8 items-center">
          <div>
            <span className="inline-block text-xs font-bold uppercase tracking-wide text-green-dark mb-3">
              Motto: Forward Together with PBAT
            </span>
            <h1 className="text-4xl font-extrabold text-ink leading-tight mb-4">
              Mobilising every polling unit for Tinubu 2027
            </h1>
            <p className="text-muted mb-6">
              Register as a GAT member, get your personal referral code, bring your community on board, and report from
              the grassroots — ward by ward, polling unit by polling unit.
            </p>
            <div className="flex gap-3 flex-wrap">
              <a href={`${APP_BASE}/register`} className="inline-flex items-center px-5 py-3 rounded-lg font-semibold text-white bg-green hover:bg-green-dark">
                Register as a member
              </a>
              <a href={`${APP_BASE}/login`} className="inline-flex items-center px-5 py-3 rounded-lg font-semibold text-white bg-red hover:opacity-90">
                Member login
              </a>
              <a
                href={`${APP_BASE}/docs/GAT-2027-Brochure.pdf`}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 px-5 py-3 rounded-lg font-semibold text-ink bg-white border border-line hover:bg-background"
              >
                ⬇ Download brochure
              </a>
            </div>
          </div>
          <img src={`${APP_BASE}/img/tinubu.jpg`} alt="President Bola Ahmed Tinubu" className="rounded-xl max-h-96 w-full object-cover shadow-lg" />
        </div>
      </section>

      <SiteFooter />
    </>
  );
}

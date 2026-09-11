import { APP_BASE, getEvents } from "@/lib/api";
import EventList from "@/components/EventList";

export const revalidate = 60;

export default async function Home() {
  const events = await getEvents();
  const upcoming = events.filter((e) => e.upcoming);
  const past = events.filter((e) => !e.upcoming);

  return (
    <>
      <div className="h-1.5 w-full" style={{ background: "linear-gradient(90deg, var(--green) 0 33%, #fff 33% 66%, var(--red) 66%)" }} />

      <header className="bg-white border-b border-line sticky top-0 z-50">
        <div className="max-w-6xl mx-auto px-4 py-3 flex items-center gap-4">
          <div className="flex items-center gap-3">
            <img src={`${APP_BASE}/img/logo.jpg`} alt="GAT 2027" className="h-12 w-auto rounded" />
            <div>
              <div className="font-bold text-ink leading-tight">Grassroot Advocacy for Tinubu</div>
              <div className="text-xs text-muted">GAT 2027 · Forward Together with PBAT</div>
            </div>
          </div>
          <nav className="ml-auto flex gap-1">
            <a href="#events" className="px-3 py-2 rounded-lg font-medium text-ink hover:bg-green-light hover:text-green-dark">
              Events
            </a>
            <a href={`${APP_BASE}/login`} className="px-3 py-2 rounded-lg font-medium text-ink hover:bg-green-light hover:text-green-dark">
              Log in
            </a>
          </nav>
        </div>
      </header>

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
            </div>
          </div>
          <img src={`${APP_BASE}/img/tinubu.jpg`} alt="President Bola Ahmed Tinubu" className="rounded-xl max-h-96 w-full object-cover shadow-lg" />
        </div>
      </section>

      <main className="max-w-6xl mx-auto px-4 py-14 flex-1 w-full" id="events">
        <div className="mb-10">
          <h2 className="text-2xl font-bold text-ink mb-1">Events</h2>
          <p className="text-muted">Rallies, town halls and mobilisation drives from across the country.</p>
        </div>

        {upcoming.length > 0 && (
          <div className="mb-10">
            <h3 className="text-lg font-bold text-ink mb-4">Upcoming</h3>
            <EventList events={upcoming} />
          </div>
        )}

        {past.length > 0 && (
          <div>
            <h3 className="text-lg font-bold text-ink mb-4">Past events</h3>
            <EventList events={past} />
          </div>
        )}

        {events.length === 0 && <EventList events={[]} />}
      </main>

      <footer className="bg-ink text-white text-center py-6 text-sm">
        <b>GAT</b>
        <br />
        Grassroot Advocacy for Tinubu
        <br />
        GAT 2027 · Forward Together with PBAT
      </footer>
    </>
  );
}

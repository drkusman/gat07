import { getEvents } from "@/lib/api";
import EventList from "@/components/EventList";
import SiteHeader from "@/components/SiteHeader";
import SiteFooter from "@/components/SiteFooter";

export const revalidate = 60;

export default async function EventsPage() {
  const events = await getEvents();
  const upcoming = events.filter((e) => e.upcoming);
  const past = events.filter((e) => !e.upcoming);

  return (
    <>
      <SiteHeader active="/events" />

      <main className="max-w-6xl mx-auto px-4 py-14 flex-1 w-full">
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

      <SiteFooter />
    </>
  );
}

import type { GatEvent } from "@/lib/api";

function formatDate(iso: string) {
  return new Date(iso + "T00:00:00").toLocaleDateString("en-NG", { day: "numeric", month: "short", year: "numeric" });
}

function MediaLinks({ event }: { event: GatEvent }) {
  if (!event.hasMedia) {
    return <span className="text-sm text-muted italic">Photos &amp; videos coming soon</span>;
  }
  return (
    <div className="flex gap-3 flex-wrap">
      {event.videoUrl && (
        <a href={event.videoUrl} target="_blank" rel="noopener noreferrer" className="text-sm font-semibold text-blue hover:underline">
          ▶ Watch video
        </a>
      )}
      {event.photoGalleryUrl && (
        <a href={event.photoGalleryUrl} target="_blank" rel="noopener noreferrer" className="text-sm font-semibold text-green hover:underline">
          🖼 View photos
        </a>
      )}
    </div>
  );
}

export default function EventList({ events }: { events: GatEvent[] }) {
  if (events.length === 0) {
    return (
      <div className="rounded-xl border border-line bg-white p-8 text-center text-muted">
        No events published yet — check back soon.
      </div>
    );
  }

  return (
    <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
      {events.map((event) => (
        <article key={event.id} className="rounded-xl border border-line bg-white shadow-sm overflow-hidden flex flex-col">
          <div className="aspect-[16/9] bg-green-light flex items-center justify-center overflow-hidden">
            {event.coverImageUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={event.coverImageUrl} alt={event.title} className="w-full h-full object-cover" />
            ) : (
              <span className="text-4xl">📅</span>
            )}
          </div>
          <div className="p-4 flex flex-col gap-2 flex-1">
            {event.upcoming && (
              <span className="self-start text-xs font-bold uppercase tracking-wide text-gold bg-[#fff6e0] px-2 py-0.5 rounded">
                Upcoming
              </span>
            )}
            <h3 className="text-lg font-bold text-ink">{event.title}</h3>
            <div className="text-sm text-muted">
              {formatDate(event.date)}
              {event.location ? ` · ${event.location}` : ""}
            </div>
            <p className="text-sm text-muted flex-1">{event.description}</p>
            <div className="pt-2 mt-auto border-t border-line">
              <MediaLinks event={event} />
            </div>
          </div>
        </article>
      ))}
    </div>
  );
}

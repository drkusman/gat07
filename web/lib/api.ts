// Base URL of the Spring Boot backend. Register, login and the member/admin
// dashboards keep living there — this Next.js app only owns the public site.
export const API_BASE = process.env.API_BASE ?? process.env.NEXT_PUBLIC_APP_BASE ?? "http://localhost:8080";
export const APP_BASE = process.env.NEXT_PUBLIC_APP_BASE ?? "http://localhost:8080";

export type GatEvent = {
  id: number;
  title: string;
  slug: string;
  date: string;
  location: string | null;
  description: string;
  videoUrl: string | null;
  hasMedia: boolean;
  upcoming: boolean;
};

export async function getEvents(): Promise<GatEvent[]> {
  try {
    const res = await fetch(`${API_BASE}/api/events`, { next: { revalidate: 60 } });
    if (!res.ok) return [];
    return (await res.json()) as GatEvent[];
  } catch {
    return [];
  }
}

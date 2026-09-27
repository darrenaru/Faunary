-- Routes of explorers who share their live location: the line still ahead of them and where they're
-- heading, so others can see it on the map in real time. Opt-in (same switch as live location),
-- one row per user, removed when the trip ends. The app re-sends it at least every 20 s, so a row
-- older than a minute belongs to an app that was killed mid-trip and is hidden.
-- Run once in the Supabase SQL editor after 0009.

create table public.live_routes (
  user_id uuid primary key default auth.uid() references auth.users on delete cascade,
  display_name text not null default 'Penjelajah' check (char_length(display_name) between 1 and 32),
  dest_label text not null check (char_length(dest_label) between 1 and 80),
  dest_lat double precision not null check (dest_lat between -90 and 90),
  dest_lng double precision not null check (dest_lng between -180 and 180),
  mode text not null check (mode in ('walking', 'driving')),
  -- [[lat, lng], …] of the route ahead, simplified by the app.
  path jsonb not null check (jsonb_typeof(path) = 'array' and jsonb_array_length(path) between 2 and 500),
  remaining_meters real,
  updated_at timestamptz not null default now()
);

alter table public.live_routes enable row level security;

create policy "fresh live routes are visible to signed-in users"
  on public.live_routes for select to authenticated
  using (user_id = auth.uid() or updated_at > now() - interval '1 minute');
create policy "users insert their own live route"
  on public.live_routes for insert to authenticated with check (user_id = auth.uid());
create policy "users update their own live route"
  on public.live_routes for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "users delete their own live route"
  on public.live_routes for delete to authenticated using (user_id = auth.uid());

-- Server clock, like live locations (touch_updated_at comes from 0001).
create trigger live_routes_touch before insert or update on public.live_routes
  for each row execute function public.touch_updated_at();

alter table public.live_routes replica identity full;
alter publication supabase_realtime add table public.live_routes;

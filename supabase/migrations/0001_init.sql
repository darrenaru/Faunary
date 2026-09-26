-- Faunary backend schema.
-- Run once in the Supabase SQL editor (or `supabase db push`).
-- Also enable: Authentication → Sign In / Providers → "Allow anonymous sign-ins".

create extension if not exists postgis with schema extensions;

-- ---------------------------------------------------------------------------
-- Profiles: public display name per (anonymous) user
-- ---------------------------------------------------------------------------
create table public.profiles (
  id uuid primary key references auth.users on delete cascade,
  display_name text not null default 'Penjelajah' check (char_length(display_name) between 1 and 32),
  updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

create policy "profiles are readable by signed-in users"
  on public.profiles for select to authenticated using (true);
create policy "users insert their own profile"
  on public.profiles for insert to authenticated with check (id = auth.uid());
create policy "users update their own profile"
  on public.profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

-- ---------------------------------------------------------------------------
-- Sightings: every entry is public (product decision), owner can edit/delete
-- ---------------------------------------------------------------------------
create table public.sightings (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users on delete cascade,
  animal_label text not null check (char_length(animal_label) between 1 and 80),
  category text not null,
  confidence real not null default 0,
  ai_label text,
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  location geography(point, 4326)
    generated always as (extensions.st_setsrid(extensions.st_makepoint(longitude, latitude), 4326)::geography) stored,
  location_name text,
  note text check (char_length(note) <= 1000),
  photo_path text not null,
  photo_width int not null,
  photo_height int not null,
  detections jsonb not null default '[]'::jsonb,
  taken_at_ms bigint not null,
  created_at timestamptz not null default now()
);

create index sightings_location_idx on public.sightings using gist (location);
create index sightings_lat_lng_idx on public.sightings (latitude, longitude);
create index sightings_taken_idx on public.sightings (taken_at_ms desc);

alter table public.sightings enable row level security;

create policy "sightings are public to signed-in users"
  on public.sightings for select to authenticated using (true);
create policy "users insert their own sightings"
  on public.sightings for insert to authenticated with check (user_id = auth.uid());
create policy "users update their own sightings"
  on public.sightings for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "users delete their own sightings"
  on public.sightings for delete to authenticated using (user_id = auth.uid());

-- Read model for the map: sighting + finder name.
create view public.community_sightings with (security_invoker = true) as
  select s.id, s.user_id, s.animal_label, s.category, s.confidence, s.ai_label,
         s.latitude, s.longitude, s.location_name, s.note, s.photo_path,
         s.photo_width, s.photo_height, s.detections, s.taken_at_ms,
         coalesce(p.display_name, 'Penjelajah') as display_name
  from public.sightings s
  left join public.profiles p on p.id = s.user_id;

-- ---------------------------------------------------------------------------
-- Live locations: opt-in, one row per user, only fresh rows are visible
-- ---------------------------------------------------------------------------
create table public.live_locations (
  user_id uuid primary key default auth.uid() references auth.users on delete cascade,
  display_name text not null default 'Penjelajah',
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  accuracy real,
  updated_at timestamptz not null default now()
);

alter table public.live_locations enable row level security;

create policy "fresh live locations are visible to signed-in users"
  on public.live_locations for select to authenticated
  using (user_id = auth.uid() or updated_at > now() - interval '5 minutes');
create policy "users insert their own live location"
  on public.live_locations for insert to authenticated with check (user_id = auth.uid());
create policy "users update their own live location"
  on public.live_locations for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "users delete their own live location"
  on public.live_locations for delete to authenticated using (user_id = auth.uid());

-- Server-side timestamp so clients can't fake freshness.
create function public.touch_updated_at() returns trigger language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end $$;

create trigger live_locations_touch before insert or update on public.live_locations
  for each row execute function public.touch_updated_at();

-- ---------------------------------------------------------------------------
-- Realtime
-- ---------------------------------------------------------------------------
alter table public.sightings replica identity full;
alter table public.live_locations replica identity full;
alter publication supabase_realtime add table public.sightings, public.live_locations;

-- ---------------------------------------------------------------------------
-- Storage: public photo bucket, users write only inside their own folder
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('sighting-photos', 'sighting-photos', true, 5242880, array['image/jpeg'])
on conflict (id) do nothing;

create policy "users upload photos to their own folder"
  on storage.objects for insert to authenticated
  with check (bucket_id = 'sighting-photos' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "users delete their own photos"
  on storage.objects for delete to authenticated
  using (bucket_id = 'sighting-photos' and (storage.foldername(name))[1] = auth.uid()::text);

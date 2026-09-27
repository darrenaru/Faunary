-- Shared map markers: a user long-presses a spot on the map, names it, and everyone can see it and
-- get a route there. Only the creator can delete their markers.
-- Run once in the Supabase SQL editor after 0001–0006.

create table public.map_pins (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users on delete cascade,
  title text not null check (char_length(btrim(title)) between 1 and 60),
  note text check (note is null or char_length(note) <= 300),
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  created_at timestamptz not null default now()
);

create index map_pins_user_idx on public.map_pins (user_id);
create index map_pins_created_idx on public.map_pins (created_at desc);

alter table public.map_pins enable row level security;

create policy "map pins are readable by signed-in users"
  on public.map_pins for select to authenticated using (true);
create policy "users add their own map pins"
  on public.map_pins for insert to authenticated with check (user_id = auth.uid());
create policy "users delete their own map pins"
  on public.map_pins for delete to authenticated using (user_id = auth.uid());

-- Server clock, and a cap per user so nobody can flood the shared map.
create function public.map_pins_before_insert() returns trigger
  language plpgsql security definer set search_path = public as $$
begin
  new.created_at := now();
  if (select count(*) from map_pins where user_id = new.user_id) >= 50 then
    raise exception 'map pin limit reached' using errcode = 'P0001';
  end if;
  return new;
end $$;

create trigger map_pins_before_insert before insert on public.map_pins
  for each row execute function public.map_pins_before_insert();

-- Read model: marker + creator name.
create view public.map_pin_list with (security_invoker = true) as
  select m.id, m.user_id, m.title, m.note, m.latitude, m.longitude, m.created_at,
         coalesce(p.display_name, 'Penjelajah') as display_name
  from public.map_pins m
  left join public.profiles p on p.id = m.user_id;

alter table public.map_pins replica identity full;
alter publication supabase_realtime add table public.map_pins;

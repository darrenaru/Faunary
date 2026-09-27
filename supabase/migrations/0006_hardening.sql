-- Production hardening: stricter checks on public data, and a daily cap on animal identification.
-- Run once in the Supabase SQL editor after 0001–0005.
-- Constraints are NOT VALID: they apply to every new or edited row without re-checking old ones.

-- ---------------------------------------------------------------------------
-- Sightings: only the app's categories, bounded text, photo inside the owner's folder
-- ---------------------------------------------------------------------------
alter table public.sightings
  add constraint sightings_category_check
    check (category in ('CAT', 'DOG', 'BIRD', 'WILD', 'OTHER')) not valid,
  add constraint sightings_ai_label_length
    check (ai_label is null or char_length(ai_label) <= 120) not valid,
  add constraint sightings_location_name_length
    check (location_name is null or char_length(location_name) <= 200) not valid,
  add constraint sightings_detections_size
    check (jsonb_typeof(detections) = 'array' and jsonb_array_length(detections) <= 20) not valid,
  add constraint sightings_photo_in_own_folder
    check (photo_path like user_id::text || '/%') not valid,
  add constraint sightings_photo_size
    check (photo_width between 1 and 10000 and photo_height between 1 and 10000) not valid;

-- ---------------------------------------------------------------------------
-- Live locations: same name limit as profiles
-- ---------------------------------------------------------------------------
alter table public.live_locations
  add constraint live_locations_display_name_length
    check (char_length(display_name) between 1 and 32) not valid;

-- ---------------------------------------------------------------------------
-- Animal identification quota (used by the identify-animal Edge Function)
-- ---------------------------------------------------------------------------
create table public.identify_usage (
  user_id uuid not null references auth.users on delete cascade,
  day date not null default current_date,
  count int not null default 0,
  primary key (user_id, day)
);

-- No policies: clients can't read or write it; only the function below (via the service role) does.
alter table public.identify_usage enable row level security;

-- Counts one identification and says whether the user is still within today's limit.
create function public.bump_identify_usage(p_user uuid, p_limit int) returns boolean
  language plpgsql security definer set search_path = public as $$
declare
  used int;
begin
  insert into identify_usage (user_id, day, count) values (p_user, current_date, 1)
  on conflict (user_id, day) do update set count = identify_usage.count + 1
  returning count into used;
  return used <= p_limit;
end $$;

revoke all on function public.bump_identify_usage(uuid, int) from public, anon, authenticated;
grant execute on function public.bump_identify_usage(uuid, int) to service_role;

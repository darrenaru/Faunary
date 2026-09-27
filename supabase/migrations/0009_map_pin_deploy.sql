-- Markers are deployed first (long press → it's on everyone's map right away, still unnamed) and
-- filled in afterwards by their creator. So the title may be empty until then, and the creator may
-- update the details — only title, note and icon: the spot and the owner can't change.
-- Run once in the Supabase SQL editor after 0008.

alter table public.map_pins alter column title drop not null;
alter table public.map_pins drop constraint map_pins_title_check;
alter table public.map_pins
  add constraint map_pins_title_check
    check (title is null or char_length(btrim(title)) between 1 and 60);

create policy "users update their own map pins"
  on public.map_pins for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

revoke update on public.map_pins from authenticated;
grant update (title, note, icon) on public.map_pins to authenticated;

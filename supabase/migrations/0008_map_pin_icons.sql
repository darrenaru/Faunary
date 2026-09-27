-- Map markers get an icon chosen when they're created (flag, leaf, camera, paw, star, heart),
-- so everyone sees what kind of spot it is.
-- Run once in the Supabase SQL editor after 0007.

alter table public.map_pins
  add column icon text not null default 'flag'
    check (icon in ('flag', 'leaf', 'camera', 'paw', 'star', 'heart'));

create or replace view public.map_pin_list with (security_invoker = true) as
  select m.id, m.user_id, m.title, m.note, m.latitude, m.longitude, m.created_at,
         coalesce(p.display_name, 'Penjelajah') as display_name,
         m.icon
  from public.map_pins m
  left join public.profiles p on p.id = m.user_id;

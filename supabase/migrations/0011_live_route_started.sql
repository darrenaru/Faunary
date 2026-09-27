-- When the shared route was drawn (client clock, ms): viewers play the route's draw-in animation
-- for a route that has just started, and show older ones straight away.
-- Run once in the Supabase SQL editor after 0010.

alter table public.live_routes add column started_at bigint not null default 0;

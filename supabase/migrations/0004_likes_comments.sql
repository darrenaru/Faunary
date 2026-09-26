-- Likes and comments on sightings (own and other people's).
-- Run once in the Supabase SQL editor after 0001–0003.

-- ---------------------------------------------------------------------------
-- Likes: one per user per sighting
-- ---------------------------------------------------------------------------
create table public.sighting_likes (
  sighting_id uuid not null references public.sightings on delete cascade,
  user_id uuid not null default auth.uid() references auth.users on delete cascade,
  created_at timestamptz not null default now(),
  primary key (sighting_id, user_id)
);

create index sighting_likes_user_idx on public.sighting_likes (user_id);

alter table public.sighting_likes enable row level security;

create policy "likes are readable by signed-in users"
  on public.sighting_likes for select to authenticated using (true);
create policy "users like as themselves"
  on public.sighting_likes for insert to authenticated with check (user_id = auth.uid());
create policy "users remove their own likes"
  on public.sighting_likes for delete to authenticated using (user_id = auth.uid());

-- ---------------------------------------------------------------------------
-- Comments: author can delete; the sighting's owner can also remove comments on it
-- ---------------------------------------------------------------------------
create table public.sighting_comments (
  id uuid primary key default gen_random_uuid(),
  sighting_id uuid not null references public.sightings on delete cascade,
  user_id uuid not null default auth.uid() references auth.users on delete cascade,
  body text not null check (char_length(btrim(body)) between 1 and 500),
  created_at timestamptz not null default now()
);

create index sighting_comments_sighting_idx on public.sighting_comments (sighting_id, created_at);

alter table public.sighting_comments enable row level security;

create policy "comments are readable by signed-in users"
  on public.sighting_comments for select to authenticated using (true);
create policy "users comment as themselves"
  on public.sighting_comments for insert to authenticated with check (user_id = auth.uid());
create policy "authors and sighting owners delete comments"
  on public.sighting_comments for delete to authenticated
  using (
    user_id = auth.uid()
    or exists (select 1 from public.sightings s where s.id = sighting_id and s.user_id = auth.uid())
  );

-- Server clock only: clients can't back-date comments.
create function public.comments_set_created_at() returns trigger language plpgsql as $$
begin
  new.created_at := now();
  return new;
end $$;

create trigger sighting_comments_created_at before insert on public.sighting_comments
  for each row execute function public.comments_set_created_at();

-- ---------------------------------------------------------------------------
-- Read models
-- ---------------------------------------------------------------------------
-- Comment + author name.
create view public.sighting_comment_list with (security_invoker = true) as
  select c.id, c.sighting_id, c.user_id, c.body, c.created_at,
         coalesce(p.display_name, 'Penjelajah') as display_name
  from public.sighting_comments c
  left join public.profiles p on p.id = c.user_id;

-- Per-sighting counters plus whether the caller liked it.
create view public.sighting_social with (security_invoker = true) as
  select s.id as sighting_id,
         (select count(*) from public.sighting_likes l where l.sighting_id = s.id)::int as like_count,
         (select count(*) from public.sighting_comments c where c.sighting_id = s.id)::int as comment_count,
         exists (select 1 from public.sighting_likes l where l.sighting_id = s.id and l.user_id = auth.uid()) as liked_by_me
  from public.sightings s;

-- ---------------------------------------------------------------------------
-- Realtime: detail screens refresh when someone likes or comments
-- ---------------------------------------------------------------------------
alter table public.sighting_likes replica identity full;
alter table public.sighting_comments replica identity full;
alter publication supabase_realtime add table public.sighting_likes, public.sighting_comments;

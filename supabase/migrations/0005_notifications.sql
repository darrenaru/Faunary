-- Notifications for sighting owners when someone likes or comments on their find.
-- Rows are written only by triggers (security definer); clients can read, mark read and delete their own.

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references auth.users on delete cascade,
  actor_id uuid not null references auth.users on delete cascade,
  type text not null check (type in ('like', 'comment')),
  sighting_id uuid not null references public.sightings on delete cascade,
  comment_id uuid references public.sighting_comments on delete cascade,
  created_at timestamptz not null default now(),
  read_at timestamptz
);

create index notifications_recipient_idx on public.notifications (recipient_id, created_at desc);
-- At most one like notification per actor and sighting (re-liking doesn't spam).
create unique index notifications_like_once on public.notifications (recipient_id, actor_id, sighting_id) where type = 'like';

alter table public.notifications enable row level security;

create policy "users read their notifications"
  on public.notifications for select to authenticated using (recipient_id = auth.uid());
create policy "users mark their notifications read"
  on public.notifications for update to authenticated using (recipient_id = auth.uid()) with check (recipient_id = auth.uid());
create policy "users delete their notifications"
  on public.notifications for delete to authenticated using (recipient_id = auth.uid());

-- Only read_at may change from the client.
revoke update on public.notifications from authenticated;
grant update (read_at) on public.notifications to authenticated;

-- ---------------------------------------------------------------------------
-- Triggers: likes and comments on someone else's find notify its owner
-- ---------------------------------------------------------------------------
create function public.notify_like() returns trigger
  language plpgsql security definer set search_path = public as $$
declare
  owner uuid;
begin
  if tg_op = 'INSERT' then
    select user_id into owner from sightings where id = new.sighting_id;
    if owner is not null and owner <> new.user_id then
      insert into notifications (recipient_id, actor_id, type, sighting_id)
      values (owner, new.user_id, 'like', new.sighting_id)
      on conflict (recipient_id, actor_id, sighting_id) where type = 'like'
      do update set created_at = now(), read_at = null;
    end if;
    return new;
  else
    -- Unlike: withdraw the notification.
    delete from notifications
    where type = 'like' and actor_id = old.user_id and sighting_id = old.sighting_id;
    return old;
  end if;
end $$;

create trigger sighting_likes_notify after insert or delete on public.sighting_likes
  for each row execute function public.notify_like();

create function public.notify_comment() returns trigger
  language plpgsql security definer set search_path = public as $$
declare
  owner uuid;
begin
  select user_id into owner from sightings where id = new.sighting_id;
  if owner is not null and owner <> new.user_id then
    insert into notifications (recipient_id, actor_id, type, sighting_id, comment_id)
    values (owner, new.user_id, 'comment', new.sighting_id, new.id);
  end if;
  return new;
end $$;

create trigger sighting_comments_notify after insert on public.sighting_comments
  for each row execute function public.notify_comment();

-- ---------------------------------------------------------------------------
-- Read model: who did it, what they wrote, and which photo
-- ---------------------------------------------------------------------------
create view public.notification_list with (security_invoker = true) as
  select n.id, n.type, n.sighting_id, n.comment_id, n.created_at, n.read_at,
         n.actor_id, coalesce(p.display_name, 'Penjelajah') as actor_name,
         c.body as comment_body,
         s.animal_label, s.photo_path
  from public.notifications n
  join public.sightings s on s.id = n.sighting_id
  left join public.profiles p on p.id = n.actor_id
  left join public.sighting_comments c on c.id = n.comment_id;

alter table public.notifications replica identity full;
alter publication supabase_realtime add table public.notifications;

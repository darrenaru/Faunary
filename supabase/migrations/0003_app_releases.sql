-- Public bucket for in-app updates: APKs, delta patches and latest.json (the update manifest).
-- Anyone can download; only the release script (service role, never shipped in the app) uploads.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'app-releases', 'app-releases', true, 52428800,
  array['application/vnd.android.package-archive', 'application/octet-stream', 'application/json']
)
on conflict (id) do nothing;

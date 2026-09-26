-- Photo uploads use upsert (safe retries after a partial sync), which Storage only allows
-- when the user can also SELECT and UPDATE the object. Scope both to the user's own folder.

create policy "users read their own photo objects"
  on storage.objects for select to authenticated
  using (bucket_id = 'sighting-photos' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "users overwrite their own photos"
  on storage.objects for update to authenticated
  using (bucket_id = 'sighting-photos' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'sighting-photos' and (storage.foldername(name))[1] = auth.uid()::text);

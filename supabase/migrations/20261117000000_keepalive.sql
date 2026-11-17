-- Free Supabase projects are paused after a week without activity.
-- .github/workflows/keepalive.yml calls this daily so the database sees a real query.
-- Reveals nothing: returns no data and is the only thing the anon role may execute.
create function public.keepalive()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform 1 from public.rooms limit 1;
end;
$$;

revoke execute on function public.keepalive() from public;
grant execute on function public.keepalive() to anon, authenticated;

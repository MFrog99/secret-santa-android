-- Secret Santa schema.
-- All writes that must stay consistent (create, join, draw, reveal) go through
-- SECURITY DEFINER functions; clients only get SELECT plus a few column-level UPDATEs.

-- ---------------------------------------------------------------- tables

create table public.profiles (
  id           uuid primary key references auth.users (id) on delete cascade,
  display_name text not null check (char_length(display_name) between 1 and 50),
  avatar_url   text
);

create table public.rooms (
  id         uuid primary key default gen_random_uuid(),
  name       text not null check (char_length(name) between 1 and 60),
  code       text not null unique check (code ~ '^[A-Z0-9]{6}$'),
  owner_id   uuid not null references public.profiles (id) on delete cascade,
  status     text not null default 'draft' check (status in ('draft', 'drawn')),
  created_at timestamptz not null default now()
);

create table public.room_members (
  room_id   uuid not null references public.rooms (id) on delete cascade,
  user_id   uuid not null references public.profiles (id) on delete cascade,
  joined_at timestamptz not null default now(),
  wishlist  text check (char_length(wishlist) <= 2000),
  primary key (room_id, user_id)
);
create index room_members_user_id_idx on public.room_members (user_id);

create table public.assignments (
  room_id      uuid not null references public.rooms (id) on delete cascade,
  santa_id     uuid not null references public.profiles (id) on delete cascade,
  recipient_id uuid not null references public.profiles (id) on delete cascade,
  revealed     boolean not null default false,
  primary key (room_id, santa_id),
  unique (room_id, recipient_id),
  check (santa_id <> recipient_id)
);
create index assignments_recipient_idx on public.assignments (recipient_id);

-- ---------------------------------------------------------------- profile on sign-up

create function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_name text := left(trim(coalesce(new.raw_user_meta_data ->> 'display_name', '')), 50);
begin
  if v_name = '' then
    v_name := left(split_part(coalesce(new.email, 'Участник'), '@', 1), 50);
  end if;
  insert into public.profiles (id, display_name) values (new.id, v_name);
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------- RLS helpers
-- SECURITY DEFINER so that policies on room_members do not recurse into themselves.

create function public.is_room_member(p_room uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.room_members m
    where m.room_id = p_room and m.user_id = (select auth.uid())
  );
$$;

create function public.shares_room_with(p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.room_members mine
    join public.room_members theirs using (room_id)
    where mine.user_id = (select auth.uid()) and theirs.user_id = p_user
  );
$$;

-- ---------------------------------------------------------------- RLS

alter table public.profiles     enable row level security;
alter table public.rooms        enable row level security;
alter table public.room_members enable row level security;
alter table public.assignments  enable row level security;

create policy profiles_select on public.profiles
  for select to authenticated
  using (id = (select auth.uid()) or public.shares_room_with(id));

create policy profiles_update_own on public.profiles
  for update to authenticated
  using (id = (select auth.uid()))
  with check (id = (select auth.uid()));

create policy rooms_select_member on public.rooms
  for select to authenticated
  using (public.is_room_member(id));

create policy members_select_same_room on public.room_members
  for select to authenticated
  using (public.is_room_member(room_id));

create policy members_update_own_wishlist on public.room_members
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

-- A santa always sees their own row. The recipient only sees it after the reveal,
-- so "who is my santa" stays hidden until then, even from the room owner.
create policy assignments_select on public.assignments
  for select to authenticated
  using (
    santa_id = (select auth.uid())
    or (revealed and recipient_id = (select auth.uid()))
  );

-- Supabase grants everything on public tables by default; narrow it down.
revoke all on public.profiles, public.rooms, public.room_members, public.assignments
  from anon, authenticated;
grant select on public.profiles, public.rooms, public.room_members, public.assignments
  to authenticated;
grant update (display_name, avatar_url) on public.profiles to authenticated;
grant update (wishlist) on public.room_members to authenticated;

-- ---------------------------------------------------------------- RPC

create function public.create_room(p_name text)
returns public.rooms
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := auth.uid();
  v_name text := trim(coalesce(p_name, ''));
  v_room public.rooms;
  v_code text;
  v_alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; -- no 0/O, 1/I
begin
  if v_uid is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;
  if char_length(v_name) not between 1 and 60 then
    raise exception 'INVALID_ROOM_NAME';
  end if;

  for attempt in 1..20 loop
    select string_agg(substr(v_alphabet, 1 + (get_byte(b.bytes, i) % 32), 1), '')
      into v_code
      from (select decode(replace(gen_random_uuid()::text, '-', ''), 'hex') as bytes) b,
           generate_series(0, 5) as i;
    begin
      insert into public.rooms (name, code, owner_id)
      values (v_name, v_code, v_uid)
      returning * into v_room;
      insert into public.room_members (room_id, user_id) values (v_room.id, v_uid);
      return v_room;
    exception when unique_violation then
      null; -- code collision, try another one
    end;
  end loop;
  raise exception 'CODE_GENERATION_FAILED';
end;
$$;

create function public.join_room(p_code text)
returns public.rooms
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := auth.uid();
  v_room public.rooms;
begin
  if v_uid is null then
    raise exception 'NOT_AUTHENTICATED';
  end if;

  -- The lock serialises joins against draw_room so nobody slips in mid-draw.
  select * into v_room from public.rooms
    where code = upper(trim(coalesce(p_code, '')))
    for update;
  if not found then
    raise exception 'ROOM_NOT_FOUND';
  end if;

  if exists (select 1 from public.room_members
             where room_id = v_room.id and user_id = v_uid) then
    return v_room; -- already in: joining again is harmless
  end if;
  if v_room.status <> 'draft' then
    raise exception 'ROOM_ALREADY_DRAWN';
  end if;

  insert into public.room_members (room_id, user_id) values (v_room.id, v_uid);
  return v_room;
end;
$$;

-- The draw runs on the server: if the owner's phone computed it, the owner could
-- read every pair. Result is a uniformly random derangement (no self-gifts).
create function public.draw_room(p_room uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid        uuid := auth.uid();
  v_room       public.rooms;
  v_santas     uuid[];
  v_recipients uuid[];
  v_count      int;
  v_ok         boolean;
begin
  select * into v_room from public.rooms where id = p_room for update;
  if not found then
    raise exception 'ROOM_NOT_FOUND';
  end if;
  if v_uid is null or v_room.owner_id <> v_uid then
    raise exception 'NOT_ROOM_OWNER';
  end if;
  if v_room.status <> 'draft' then
    raise exception 'ROOM_ALREADY_DRAWN';
  end if;

  select array_agg(user_id) into v_santas
    from public.room_members where room_id = p_room;
  v_count := coalesce(array_length(v_santas, 1), 0);
  if v_count < 2 then
    raise exception 'NOT_ENOUGH_MEMBERS';
  end if;

  -- Rejection sampling: about 2.7 shuffles on average.
  loop
    select array_agg(u order by gen_random_uuid()) into v_recipients
      from unnest(v_santas) as u;
    v_ok := true;
    for i in 1..v_count loop
      if v_santas[i] = v_recipients[i] then
        v_ok := false;
        exit;
      end if;
    end loop;
    exit when v_ok;
  end loop;

  insert into public.assignments (room_id, santa_id, recipient_id)
  select p_room, v_santas[i], v_recipients[i]
    from generate_series(1, v_count) as i;

  update public.rooms set status = 'drawn' where id = p_room;
end;
$$;

-- Owner opens the envelopes: every recipient can now see who their santa was.
create function public.reveal_room(p_room uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := auth.uid();
  v_room public.rooms;
begin
  select * into v_room from public.rooms where id = p_room;
  if not found then
    raise exception 'ROOM_NOT_FOUND';
  end if;
  if v_uid is null or v_room.owner_id <> v_uid then
    raise exception 'NOT_ROOM_OWNER';
  end if;
  if v_room.status <> 'drawn' then
    raise exception 'ROOM_NOT_DRAWN';
  end if;
  update public.assignments set revealed = true where room_id = p_room;
end;
$$;

revoke execute on function
  public.handle_new_user(),
  public.is_room_member(uuid),
  public.shares_room_with(uuid),
  public.create_room(text),
  public.join_room(text),
  public.draw_room(uuid),
  public.reveal_room(uuid)
  from public, anon, authenticated;

-- Helpers are called from RLS policies as the querying role, including by Realtime.
grant execute on function
  public.is_room_member(uuid),
  public.shares_room_with(uuid),
  public.create_room(text),
  public.join_room(text),
  public.draw_room(uuid),
  public.reveal_room(uuid)
  to authenticated;

-- ---------------------------------------------------------------- Realtime
-- Postgres Changes respects RLS, so assignment events only reach those allowed to read them.

alter publication supabase_realtime
  add table public.rooms, public.room_members, public.assignments;

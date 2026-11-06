-- End-to-end backend checks. Everything runs in one transaction that is ALWAYS rolled
-- back, so no users or rooms remain. Run: supabase db query --remote -f supabase/tests/backend_checks.sql
-- Failure raises an exception naming the check; success returns a single 'ALL OK' row.
begin;

create function pg_temp.act_as(p_uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', p_uid, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
end $$;

create function pg_temp.as_admin() returns void language plpgsql as $$
begin
  execute 'reset role';
  perform set_config('request.jwt.claims', '', true);
end $$;

create function pg_temp.expect_error(p_sql text, p_needle text) returns void language plpgsql as $$
begin
  execute p_sql;
  raise exception 'CHECK FAILED: expected error % from: %', p_needle, p_sql;
exception
  when others then
    if sqlerrm like 'CHECK FAILED%' or sqlerrm not like '%' || p_needle || '%' then
      raise exception 'CHECK FAILED: wanted %, got: % (%)', p_needle, sqlerrm, p_sql;
    end if;
end $$;


do $$
declare
  a uuid := gen_random_uuid();  -- owner
  b uuid := gen_random_uuid();
  c uuid := gen_random_uuid();
  x uuid := gen_random_uuid();  -- outsider
  r public.rooms;
  n int;
  run int;
begin
  insert into auth.users (id, email, raw_user_meta_data, aud, role) values
    (a, 'a@test.invalid', '{"display_name":"Анна"}', 'authenticated', 'authenticated'),
    (b, 'b@test.invalid', '{"display_name":"Борис"}', 'authenticated', 'authenticated'),
    (c, 'c@test.invalid', '{}', 'authenticated', 'authenticated'),
    (x, 'x@test.invalid', '{}', 'authenticated', 'authenticated');

  if (select display_name from public.profiles where id = a) <> 'Анна' then
    raise exception 'CHECK FAILED: profile name from metadata';
  end if;
  if (select display_name from public.profiles where id = c) <> 'c' then
    raise exception 'CHECK FAILED: profile name fallback';
  end if;

  -- create & join
  perform pg_temp.act_as(a);
  r := public.create_room('Офис');
  if r.code !~ '^[A-Z0-9]{6}$' or r.status <> 'draft' or r.owner_id <> a then
    raise exception 'CHECK FAILED: created room %', r;
  end if;
  if (select count(*) from public.room_members) <> 1 then
    raise exception 'CHECK FAILED: owner must be a member';
  end if;

  perform pg_temp.expect_error('select public.draw_room(''' || r.id || ''')', 'NOT_ENOUGH_MEMBERS');

  perform pg_temp.act_as(x);
  if (select count(*) from public.rooms) <> 0 then
    raise exception 'CHECK FAILED: outsider sees a room';
  end if;
  if (select count(*) from public.room_members) <> 0 then
    raise exception 'CHECK FAILED: outsider sees members';
  end if;
  if (select count(*) from public.profiles where id = a) <> 0 then
    raise exception 'CHECK FAILED: outsider sees a profile';
  end if;
  perform pg_temp.expect_error('select public.join_room(''NOCODE'')', 'ROOM_NOT_FOUND');

  perform pg_temp.act_as(b);
  perform public.join_room(lower(r.code));   -- case-insensitive
  perform public.join_room(r.code);          -- idempotent
  perform pg_temp.act_as(c);
  perform public.join_room(r.code);
  if (select count(*) from public.room_members) <> 3 then
    raise exception 'CHECK FAILED: member sees 3 members';
  end if;
  if (select count(*) from public.profiles) <> 3 then
    raise exception 'CHECK FAILED: member sees co-member profiles';
  end if;

  -- direct writes are blocked
  perform pg_temp.expect_error('insert into public.rooms (name, code, owner_id) values (''h'',''HACKED'',''' || c || ''')', 'permission denied');
  perform pg_temp.expect_error('insert into public.assignments (room_id, santa_id, recipient_id) values (''' || r.id || ''',''' || c || ''',''' || a || ''')', 'permission denied');
  perform pg_temp.expect_error('update public.rooms set status = ''drawn''', 'permission denied');
  perform pg_temp.expect_error('update public.room_members set user_id = ''' || x || '''', 'permission denied');

  -- wishlist: own row only
  update public.room_members set wishlist = 'Книгу' where room_id = r.id;
  if (select count(*) from public.room_members where wishlist is not null) <> 1 then
    raise exception 'CHECK FAILED: wishlist update must touch only own row';
  end if;

  -- only the owner draws
  perform pg_temp.expect_error('select public.draw_room(''' || r.id || ''')', 'NOT_ROOM_OWNER');
  perform pg_temp.act_as(x);
  perform pg_temp.expect_error('select public.draw_room(''' || r.id || ''')', 'NOT_ROOM_OWNER');

  perform pg_temp.act_as(a);
  perform pg_temp.expect_error('select public.reveal_room(''' || r.id || ''')', 'ROOM_NOT_DRAWN');
  perform public.draw_room(r.id);
  perform pg_temp.expect_error('select public.draw_room(''' || r.id || ''')', 'ROOM_ALREADY_DRAWN');

  -- anonymity: everyone, the owner included, sees exactly their own row
  foreach run in array array[1, 2, 3] loop
    perform pg_temp.act_as(case run when 1 then a when 2 then b else c end);
    select count(*) into n from public.assignments
      where santa_id = (case run when 1 then a when 2 then b else c end);
    if n <> 1 or (select count(*) from public.assignments) <> 1 then
      raise exception 'CHECK FAILED: user % must see only own assignment before reveal', run;
    end if;
  end loop;
  perform pg_temp.act_as(x);
  if (select count(*) from public.assignments) <> 0 then
    raise exception 'CHECK FAILED: outsider sees assignments';
  end if;
  perform pg_temp.expect_error('select public.join_room(''' || r.code || ''')', 'ROOM_ALREADY_DRAWN');

  perform pg_temp.act_as(b);
  perform pg_temp.expect_error('update public.assignments set revealed = true', 'permission denied');
  perform pg_temp.expect_error('select public.reveal_room(''' || r.id || ''')', 'NOT_ROOM_OWNER');

  -- integrity
  perform pg_temp.as_admin();
  if (select count(*) from public.assignments where room_id = r.id) <> 3
     or (select count(distinct recipient_id) from public.assignments where room_id = r.id) <> 3
     or exists (select 1 from public.assignments where santa_id = recipient_id) then
    raise exception 'CHECK FAILED: draw is not a derangement';
  end if;
  if (select status from public.rooms where id = r.id) <> 'drawn' then
    raise exception 'CHECK FAILED: room status';
  end if;

  -- reveal: each user now sees own row and the row of their santa
  perform pg_temp.act_as(a);
  perform public.reveal_room(r.id);
  perform pg_temp.act_as(b);
  if (select count(*) from public.assignments where recipient_id = b) <> 1
     or (select count(*) from public.assignments) <> 2 then
    raise exception 'CHECK FAILED: after reveal a user sees own row + santa row';
  end if;

  -- anon gets nothing
  perform pg_temp.as_admin();
  execute 'set local role anon';
  perform pg_temp.expect_error('select * from public.rooms', 'permission denied');
  perform pg_temp.expect_error('select public.create_room(''x'')', 'permission denied');
  execute 'reset role';

  -- many draws with 2 and 5 members: never a self-gift
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  for run in 1..200 loop
    r := public.create_room('r' || run);
    insert into public.room_members (room_id, user_id) values (r.id, b);
    if run % 2 = 0 then
      insert into public.room_members (room_id, user_id) values (r.id, c), (r.id, x);
    end if;
    perform public.draw_room(r.id);
  end loop;
  if exists (select 1 from public.assignments where santa_id = recipient_id) then
    raise exception 'CHECK FAILED: self-gift in repeated draws';
  end if;
end $$;

select 'ALL OK' as result;

rollback;

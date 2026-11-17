// Web version of the Secret Santa app, for iPhone owners and anyone without Android.
// Talks to the same Supabase project as the Android app: same accounts, rooms and rules.
// All protection lives in the database (RLS + SECURITY DEFINER functions), not here.
import { createClient } from 'https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.119.0/+esm';

const { SUPABASE_URL, SUPABASE_ANON_KEY } = window.SANTA_CONFIG || {};
const app = document.getElementById('app');
const signOutButton = document.getElementById('signout');

if (!SUPABASE_URL || !SUPABASE_ANON_KEY) {
  app.textContent = 'Сайт не настроен: нет config.js с адресом сервера.';
  throw new Error('config.js is missing');
}

const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

// ------------------------------------------------------------------ helpers

/** Builds DOM nodes. Text always goes through text nodes, so user input is never parsed as HTML. */
function h(tag, props = {}, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value == null || value === false) continue;
    if (key.startsWith('on')) el.addEventListener(key.slice(2), value);
    else if (key in el) el[key] = value;
    else el.setAttribute(key, value);
  }
  for (const child of children.flat()) {
    if (child == null || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

const KNOWN_ERRORS = [
  ['ROOM_NOT_FOUND', 'Комната с таким кодом не найдена'],
  ['ROOM_ALREADY_DRAWN', 'Жеребьёвка уже проведена, войти в комнату нельзя'],
  ['ROOM_NOT_DRAWN', 'Сначала нужно провести жеребьёвку'],
  ['NOT_ENOUGH_MEMBERS', 'Для жеребьёвки нужно минимум 2 участника'],
  ['NOT_ROOM_OWNER', 'Это может сделать только владелец комнаты'],
  ['INVALID_ROOM_NAME', 'Название комнаты: от 1 до 60 символов'],
  ['invalid_credentials', 'Неверный email или пароль'],
  ['invalid login credentials', 'Неверный email или пароль'],
  ['user_already_exists', 'Пользователь с таким email уже зарегистрирован'],
  ['already registered', 'Пользователь с таким email уже зарегистрирован'],
  ['weak_password', 'Слишком простой пароль'],
  ['email_address_invalid', 'Введите корректный email'],
  ['rate_limit', 'Слишком много попыток, подождите немного'],
  ['Failed to fetch', 'Нет связи с сервером. Проверьте интернет'],
  ['NetworkError', 'Нет связи с сервером. Проверьте интернет'],
  ['Load failed', 'Нет связи с сервером. Проверьте интернет'],
];

function messageFor(error) {
  const text = `${error?.code ?? ''} ${error?.message ?? error ?? ''}`;
  const hit = KNOWN_ERRORS.find(([needle]) => text.toLowerCase().includes(needle.toLowerCase()));
  return hit ? hit[1] : 'Что-то пошло не так. Попробуйте ещё раз';
}

/** Unwraps a supabase-js result, throwing its error so callers can use try/catch. */
async function q(promise) {
  const { data, error } = await promise;
  if (error) throw error;
  return data;
}

function errorBox() {
  const box = h('p', { className: 'error', hidden: true, role: 'alert' });
  box.show = (e) => { box.textContent = typeof e === 'string' ? e : messageFor(e); box.hidden = false; };
  box.clear = () => { box.hidden = true; };
  return box;
}

/** Disables the button while the action runs and reports failures in the given box. */
async function busy(button, box, action) {
  if (button.disabled) return;
  button.disabled = true;
  box.clear();
  try {
    await action();
  } catch (e) {
    console.error(e);
    box.show(e);
  } finally {
    button.disabled = false;
  }
}

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

// ------------------------------------------------------------------ routing

let session = null;
let cleanup = null; // stops the realtime subscription of the current screen
let route = 0; // bumped on every navigation, so a screen can tell it was left mid-load

function render() {
  if (cleanup) { cleanup(); cleanup = null; }
  route += 1;
  signOutButton.hidden = !session;
  app.replaceChildren();
  const path = location.hash.replace(/^#/, '') || '/';

  const join = path.match(/^\/join\/([A-Za-z0-9]{6})$/);
  if (join) sessionStorage.setItem('pendingJoin', join[1].toUpperCase());

  if (!session) return renderAuth();
  const room = path.match(/^\/room\/([0-9a-f-]{36})$/);
  if (room) return renderRoom(room[1]);
  return renderHome();
}

window.addEventListener('hashchange', render);
signOutButton.addEventListener('click', () => supabase.auth.signOut());

supabase.auth.onAuthStateChange((event, newSession) => {
  const changedUser = session?.user?.id !== newSession?.user?.id;
  session = newSession;
  // Token refreshes keep the same user: re-rendering would wipe what is being typed.
  if (event === 'INITIAL_SESSION' || changedUser) setTimeout(render, 0);
});

// ------------------------------------------------------------------ auth

function renderAuth() {
  let mode = 'signin';
  const errors = errorBox();
  const name = h('input', { autocomplete: 'nickname', maxLength: 50 });
  const email = h('input', { type: 'email', autocomplete: 'email', inputMode: 'email', autocapitalize: 'off' });
  const password = h('input', { type: 'password', autocomplete: 'current-password' });
  const nameField = h('div', {}, h('label', {}, 'Ваше имя (его увидят участники)'), name);
  const submit = h('button', { type: 'submit' });
  const signInTab = h('button', { type: 'button' }, 'Вход');
  const signUpTab = h('button', { type: 'button' }, 'Регистрация');

  const setMode = (m) => {
    mode = m;
    signInTab.classList.toggle('active', m === 'signin');
    signUpTab.classList.toggle('active', m === 'signup');
    nameField.hidden = m === 'signin';
    password.autocomplete = m === 'signin' ? 'current-password' : 'new-password';
    submit.textContent = m === 'signin' ? 'Войти' : 'Зарегистрироваться';
    errors.clear();
  };
  signInTab.onclick = () => setMode('signin');
  signUpTab.onclick = () => setMode('signup');

  const form = h('form', {
    className: 'card',
    onsubmit: (e) => {
      e.preventDefault();
      const mail = email.value.trim();
      if (mode === 'signup' && !name.value.trim()) return errors.show('Введите имя');
      if (!EMAIL.test(mail)) return errors.show('Введите корректный email');
      if (password.value.length < 8) return errors.show('Пароль должен быть не короче 8 символов');
      busy(submit, errors, async () => {
        if (mode === 'signin') {
          await q(supabase.auth.signInWithPassword({ email: mail, password: password.value }));
        } else {
          const data = await q(supabase.auth.signUp({
            email: mail,
            password: password.value,
            options: { data: { display_name: name.value.trim() } },
          }));
          if (!data.session) errors.show('Аккаунт создан. Подтвердите email по ссылке из письма и войдите');
        }
      });
    },
  },
    h('div', { className: 'tabs' }, signInTab, signUpTab),
    nameField,
    h('label', {}, 'Email'), email,
    h('label', {}, 'Пароль (не короче 8 символов)'), password,
    submit,
  );

  setMode('signin');
  const pending = sessionStorage.getItem('pendingJoin');
  app.append(
    h('h1', {}, 'Добро пожаловать'),
    pending && h('p', { className: 'notice' }, `Войдите или зарегистрируйтесь, чтобы попасть в комнату ${pending}.`),
    errors,
    form,
    h('p', { className: 'muted center' },
      'Это тот же аккаунт, что и в Android-приложении: входите с любого устройства.'),
  );
}

// ------------------------------------------------------------------ home

async function renderHome() {
  const errors = errorBox();
  const list = h('ul', { className: 'list' }, h('li', { className: 'muted' }, 'Загрузка…'));

  const roomName = h('input', { placeholder: 'Например, «Офис 2026»', maxLength: 60 });
  const createButton = h('button', { type: 'submit' }, 'Создать комнату');
  const createForm = h('form', {
    className: 'card',
    onsubmit: (e) => {
      e.preventDefault();
      const value = roomName.value.trim();
      if (!value) return errors.show('Введите название комнаты');
      busy(createButton, errors, async () => {
        const room = await q(supabase.rpc('create_room', { p_name: value }));
        location.hash = `#/room/${room.id}`;
      });
    },
  }, h('h2', {}, 'Новая комната'), roomName, createButton);

  const code = h('input', { placeholder: 'Код из 6 символов', maxLength: 6, autocapitalize: 'characters', autocomplete: 'off' });
  const joinButton = h('button', { type: 'submit' }, 'Войти');
  const joinByCode = (value, button) => busy(button, errors, async () => {
    const room = await q(supabase.rpc('join_room', { p_code: value }));
    location.hash = `#/room/${room.id}`;
  });
  const joinForm = h('form', {
    className: 'card',
    onsubmit: (e) => {
      e.preventDefault();
      const value = code.value.trim().toUpperCase();
      if (!/^[A-Z0-9]{6}$/.test(value)) return errors.show('Код состоит из 6 букв и цифр');
      joinByCode(value, joinButton);
    },
  }, h('h2', {}, 'Войти по коду'), h('div', { className: 'row' }, code, joinButton));

  app.append(
    h('h1', {}, 'Мои комнаты'),
    errors,
    h('div', { className: 'card' }, list),
    createForm,
    joinForm,
  );

  // Arrived through an invite link: join right away.
  const pending = sessionStorage.getItem('pendingJoin');
  if (pending) {
    sessionStorage.removeItem('pendingJoin');
    code.value = pending;
    joinByCode(pending, joinButton);
  }

  try {
    const rooms = await q(supabase.from('rooms').select('id,name,code,status').order('created_at', { ascending: false }));
    list.replaceChildren(...(rooms.length
      ? rooms.map((r) => h('li', {}, h('a', { href: `#/room/${r.id}` },
          h('span', {}, r.name),
          h('span', { className: `badge ${r.status}` }, r.status === 'drawn' ? 'Жеребьёвка проведена' : 'Набор участников'))))
      : [h('li', { className: 'muted' }, 'Пока нет комнат. Создайте свою или войдите по коду.')]));
  } catch (e) {
    list.replaceChildren(h('li', { className: 'muted' }, 'Не удалось загрузить комнаты'));
    errors.show(e);
  }
}

// ------------------------------------------------------------------ room

async function renderRoom(roomId) {
  const me = session.user.id;
  const errors = errorBox();
  const header = h('div');
  const membersCard = h('div', { className: 'card' });
  const actionsCard = h('div', { className: 'card', hidden: true });
  const resultCard = h('div', { className: 'card', hidden: true });
  let targetShown = false;

  // Wishlist editor is built once so live reloads never overwrite what is being typed.
  const wish = h('textarea', { maxLength: 2000, placeholder: 'Что вам хотелось бы получить?' });
  const wishSave = h('button', { type: 'submit' }, 'Сохранить пожелание');
  const wishErrors = errorBox();
  const wishNote = h('p', { className: 'muted', hidden: true }, 'Сохранено');
  const wishForm = h('form', {
    className: 'card',
    onsubmit: (e) => {
      e.preventDefault();
      wishNote.hidden = true;
      busy(wishSave, wishErrors, async () => {
        await q(supabase.from('room_members')
          .update({ wishlist: wish.value.trim() || null })
          .eq('room_id', roomId).eq('user_id', me));
        wishNote.hidden = false;
      });
    },
  }, h('h2', {}, 'Моё пожелание'), wishErrors, wish, wishSave, wishNote);

  app.append(
    h('p', {}, h('a', { href: '#/' }, '← Мои комнаты')),
    errors, header, resultCard, actionsCard, wishForm, membersCard,
  );

  let first = true;
  async function load() {
    try {
      const [room, members, assignments] = await Promise.all([
        q(supabase.from('rooms').select('id,name,code,status,owner_id').eq('id', roomId).single()),
        q(supabase.from('room_members').select('user_id,wishlist,profiles(display_name)')
          .eq('room_id', roomId).order('joined_at', { ascending: true })),
        q(supabase.from('assignments').select('santa_id,recipient_id,revealed').eq('room_id', roomId)),
      ]);
      const nameOf = (id) => members.find((m) => m.user_id === id)?.profiles?.display_name ?? 'Участник';
      const isOwner = room.owner_id === me;
      const drawn = room.status === 'drawn';

      if (first) {
        first = false;
        wish.value = members.find((m) => m.user_id === me)?.wishlist ?? '';
      }

      // Header: name, status and how to invite.
      const inviteLink = `${location.origin}${location.pathname}#/join/${room.code}`;
      const copy = h('button', { className: 'secondary', type: 'button' }, 'Скопировать приглашение');
      copy.onclick = async () => {
        const text = `Тайный Санта «${room.name}». Код комнаты: ${room.code}\nВойти через сайт: ${inviteLink}`;
        try {
          if (navigator.share) await navigator.share({ text });
          else { await navigator.clipboard.writeText(text); copy.textContent = 'Скопировано'; }
        } catch { /* user closed the share sheet */ }
      };
      header.replaceChildren(
        h('h1', {}, room.name),
        h('div', { className: 'card' },
          h('span', { className: `badge ${room.status}` }, drawn ? 'Жеребьёвка проведена' : 'Набор участников'),
          drawn
            ? h('p', { className: 'muted' }, 'Новые участники больше не могут войти.')
            : [h('p', { className: 'muted' }, 'Код комнаты для друзей:'), h('div', { className: 'code' }, room.code), copy]),
      );

      // Members.
      membersCard.replaceChildren(
        h('h2', {}, `Участники (${members.length})`),
        h('ul', { className: 'list' }, members.map((m) => h('li', {},
          m.profiles?.display_name ?? 'Участник',
          m.user_id === room.owner_id && h('span', { className: 'muted' }, ' · владелец'),
          m.user_id === me && h('span', { className: 'muted' }, ' · вы')))),
      );

      // Owner controls.
      actionsCard.hidden = !isOwner || assignments.some((a) => a.revealed);
      if (isOwner && !drawn) {
        const drawButton = h('button', { disabled: members.length < 2 }, 'Начать жеребьёвку');
        drawButton.onclick = () => {
          if (!confirm('После жеребьёвки новые участники не смогут войти. Начать?')) return;
          busy(drawButton, errors, async () => { await q(supabase.rpc('draw_room', { p_room: roomId })); await load(); });
        };
        actionsCard.replaceChildren(
          h('h2', {}, 'Жеребьёвка'),
          h('p', { className: 'muted' }, members.length < 2
            ? 'Нужно минимум 2 участника. Отправьте друзьям код комнаты.'
            : 'Когда все соберутся, начните жеребьёвку. Пары распределит сервер, их не увидит даже владелец.'),
          drawButton,
        );
      } else if (isOwner && drawn) {
        const revealButton = h('button', { className: 'secondary' }, 'Раскрыть всех Сант');
        revealButton.onclick = () => {
          if (!confirm('Каждый участник увидит, кто был его Сантой. Отменить это нельзя. Раскрыть?')) return;
          busy(revealButton, errors, async () => { await q(supabase.rpc('reveal_room', { p_room: roomId })); await load(); });
        };
        actionsCard.replaceChildren(
          h('h2', {}, 'После вручения подарков'),
          h('p', { className: 'muted' }, 'Можно раскрыть, кто кому дарил.'),
          revealButton,
        );
      }

      // My target and, after the reveal, my santa.
      const mine = assignments.find((a) => a.santa_id === me);
      const mySanta = assignments.find((a) => a.recipient_id === me && a.revealed);
      resultCard.hidden = !drawn;
      if (drawn && mine) {
        const recipient = members.find((m) => m.user_id === mine.recipient_id);
        const showButton = h('button', {}, 'Показать мою цель');
        showButton.onclick = () => { targetShown = true; load(); };
        resultCard.replaceChildren(
          h('h2', {}, 'Вы дарите подарок'),
          targetShown
            ? [h('div', { className: 'target' }, nameOf(mine.recipient_id)),
               h('p', { className: 'muted' }, 'Пожелание:'),
               h('p', { className: 'wish' }, recipient?.wishlist || 'Пожелание пока не указано')]
            : [h('p', { className: 'muted' }, 'Убедитесь, что никто не смотрит на экран.'), showButton],
          mySanta && [h('h2', { style: 'margin-top:16px' }, 'Ваш Санта'), h('div', { className: 'target' }, nameOf(mySanta.santa_id))],
        );
      } else if (drawn) {
        resultCard.replaceChildren(h('p', { className: 'muted' }, 'Жеребьёвка проведена без вас.'));
      }
      errors.clear();
    } catch (e) {
      console.error(e);
      errors.show(e.code === 'PGRST116' ? 'Комната не найдена или вы в ней не состоите' : e);
    }
  }

  const myRoute = route;
  await load();
  if (myRoute !== route) return; // the user already navigated away

  // Live updates: reload whenever the room, its members or my assignments change.
  // The channel reconnects by itself; after a reconnect we reload to catch missed events.
  let reloadTimer = null;
  const reload = () => { clearTimeout(reloadTimer); reloadTimer = setTimeout(load, 150); };
  const channel = supabase.channel(`room-${roomId}-${crypto.randomUUID()}`)
    .on('postgres_changes', { event: '*', schema: 'public', table: 'rooms', filter: `id=eq.${roomId}` }, reload)
    .on('postgres_changes', { event: '*', schema: 'public', table: 'room_members', filter: `room_id=eq.${roomId}` }, reload)
    .on('postgres_changes', { event: '*', schema: 'public', table: 'assignments', filter: `room_id=eq.${roomId}` }, reload)
    .subscribe((status) => { if (status === 'SUBSCRIBED') reload(); });

  // Phones suspend background tabs; catch up when the page becomes visible again.
  const onVisible = () => { if (document.visibilityState === 'visible') reload(); };
  document.addEventListener('visibilitychange', onVisible);

  cleanup = () => {
    clearTimeout(reloadTimer);
    document.removeEventListener('visibilitychange', onVisible);
    supabase.removeChannel(channel);
  };
}

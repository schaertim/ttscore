import { createBrowserClient, createServerClient, isBrowser } from '@supabase/ssr';
import { PUBLIC_SUPABASE_URL, PUBLIC_SUPABASE_PUBLISHABLE_KEY } from '$env/static/public';
import type { LayoutLoad } from './$types';
import '$lib/i18n'; // registers all locale loaders
import { locale } from 'svelte-i18n';
import { resolveLocale } from '$lib/i18n';
import { STORAGE_KEYS } from '$lib/storageKeys';

export const load: LayoutLoad = async ({ data, depends, fetch }) => {
	depends('supabase:auth');

	// Initialise locale: server picks from cookie/Accept-Language; browser may refine
	const activeLocale = isBrowser()
		? resolveLocale(localStorage.getItem(STORAGE_KEYS.locale) ?? navigator.language)
		: data.locale;
	// Await locale.set directly rather than the old `locale.set(x); await waitLocale()`
	// pattern. When the locale dictionary isn't cached yet (the client's very first load),
	// set() is async and only assigns $locale once the dictionary has loaded — but
	// waitLocale() resolved immediately (getCurrentLocale() was still null and no
	// initialLocale is configured), so the layout rendered and formatted nav labels while
	// $locale was null, throwing "[svelte-i18n] Cannot format a message without first
	// setting the initial locale." Awaiting set() waits for both the load and the assignment.
	await locale.set(activeLocale);

	const supabase = isBrowser()
		? createBrowserClient(PUBLIC_SUPABASE_URL, PUBLIC_SUPABASE_PUBLISHABLE_KEY, {
				global: { fetch }
			})
		: createServerClient(PUBLIC_SUPABASE_URL, PUBLIC_SUPABASE_PUBLISHABLE_KEY, {
				global: { fetch },
				cookies: { getAll: () => data.cookies }
			});

	// On the server: use session + user already validated by safeGetSession() in
	// +layout.server.ts (which called getUser()). Never touch session.user here —
	// that object comes from cookie storage and triggers the Supabase SDK warning.
	//
	// On the browser: getSession() is fine — we're reading from localStorage/memory,
	// not untrusted cookie storage.
	if (!isBrowser()) {
		return {
			supabase,
			session: data.session,
			user: data.user,
			hasHomePlayer: data.hasHomePlayer,
			homePlayerId: data.homePlayerId,
			homePlayerClassification: data.homePlayerClassification,
			isPro: data.isPro
		};
	}

	const {
		data: { session }
	} = await supabase.auth.getSession();

	return {
		supabase,
		session,
		user: session?.user ?? null,
		hasHomePlayer: data.hasHomePlayer,
		homePlayerId: data.homePlayerId,
		homePlayerClassification: data.homePlayerClassification,
		isPro: data.isPro
	};
};

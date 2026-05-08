import { createBrowserClient, createServerClient, isBrowser } from '@supabase/ssr';
import { PUBLIC_SUPABASE_URL, PUBLIC_SUPABASE_PUBLISHABLE_KEY } from '$env/static/public';
import type { LayoutLoad } from './$types';

export const load: LayoutLoad = async ({ data, depends, fetch }) => {
	depends('supabase:auth');

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
		return { supabase, session: data.session, user: data.user };
	}

	const {
		data: { session }
	} = await supabase.auth.getSession();

	return { supabase, session, user: session?.user ?? null };
};

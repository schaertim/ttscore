import { createServerClient } from '@supabase/ssr';
import { PUBLIC_SUPABASE_URL, PUBLIC_SUPABASE_PUBLISHABLE_KEY } from '$env/static/public';
import type { Handle } from '@sveltejs/kit';

export const handle: Handle = async ({ event, resolve }) => {
	event.locals.supabase = createServerClient(PUBLIC_SUPABASE_URL, PUBLIC_SUPABASE_PUBLISHABLE_KEY, {
		cookies: {
			getAll: () => event.cookies.getAll(),
			setAll: (cookiesToSet) => {
				cookiesToSet.forEach(({ name, value, options }) => {
					event.cookies.set(name, value, { ...options, path: '/' });
				});
			}
		},
		// Route Supabase's internal auth requests (getUser / token refresh) through
		// SvelteKit's managed fetch so they don't trip the "Avoid calling fetch
		// eagerly during server-side rendering" warning.
		global: { fetch: event.fetch }
	});

	/**
	 * Validates the session by calling getUser() against the Supabase server.
	 * Never trust getSession() alone for server-side auth checks — only the
	 * getUser() response is verified with Supabase's auth server.
	 */
	event.locals.safeGetSession = async () => {
		const {
			data: { session }
		} = await event.locals.supabase.auth.getSession();
		if (!session) return { session: null, user: null };

		const {
			data: { user },
			error
		} = await event.locals.supabase.auth.getUser();
		if (error || !user) return { session: null, user: null };

		// getUser() returns the server-verified user. Swap it into the session so
		// nothing downstream (including SvelteKit serialising the load data) ever
		// touches the unverified, cookie-derived user object that getSession()
		// attaches — reading a property off that proxy is what logs Supabase's
		// "Using the user object as returned from getSession()… could be insecure" warning.
		return { session: { ...session, user }, user };
	};

	return resolve(event, {
		filterSerializedResponseHeaders(name) {
			return name === 'content-range' || name === 'x-supabase-api-version';
		}
	});
};

import type { LayoutServerLoad } from './$types';
import { authedKtor } from '$lib/server/ktor';
import { resolveLocale } from '$lib/i18n';

export const load: LayoutServerLoad = async ({ locals: { safeGetSession }, cookies, request }) => {
	const { session, user } = await safeGetSession();
	const locale = resolveLocale(
		cookies.get('ttscore_locale') ?? request.headers.get('accept-language')
	);

	let hasHomePlayer = false;
	if (session) {
		const profileRes = await authedKtor(session.access_token)
			.get('/users/me')
			.catch(() => null);
		if (profileRes?.ok) {
			const profile = await profileRes.json();
			hasHomePlayer = !!profile.homePlayerId;
		}
	}

	return {
		session,
		user,
		hasHomePlayer,
		locale,
		// Pass raw cookies to +layout.ts so it can reconstruct the Supabase client
		// server-side without a browser environment.
		cookies: cookies.getAll()
	};
};

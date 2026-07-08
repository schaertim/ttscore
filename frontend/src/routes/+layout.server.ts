import type { LayoutServerLoad } from './$types';
import { authedKtor } from '$lib/server/ktor';
import { resolveLocale } from '$lib/i18n';
import { api } from '$lib/api';

export const load: LayoutServerLoad = async ({
	locals: { safeGetSession },
	cookies,
	request,
	depends
}) => {
	// Re-run this load (recomputing hasHomePlayer / homePlayerId / isPro) whenever the
	// auth state changes — the layout calls invalidate('supabase:auth') on sign in/out.
	depends('supabase:auth');

	const { session, user } = await safeGetSession();
	const locale = resolveLocale(
		cookies.get('ttscore_locale') ?? request.headers.get('accept-language')
	);

	let hasHomePlayer = false;
	let homePlayerId: string | null = null;
	let isPro = false;
	let homePlayerClassification: string | null = null;
	if (session) {
		const profileRes = await authedKtor(session.access_token)
			.get('/users/me')
			.catch(() => null);
		if (profileRes?.ok) {
			const profile = await profileRes.json();
			homePlayerId = profile.homePlayerId ?? null;
			hasHomePlayer = !!homePlayerId;
			isPro = profile.isPro ?? false;
		}
		if (homePlayerId) {
			const player = await api.players.get(homePlayerId).catch(() => null);
			homePlayerClassification = player?.classification ?? null;
		}
	}

	return {
		session,
		user,
		hasHomePlayer,
		homePlayerId,
		homePlayerClassification,
		isPro,
		locale,
		// Pass raw cookies to +layout.ts so it can reconstruct the Supabase client
		// server-side without a browser environment.
		cookies: cookies.getAll()
	};
};

import { error } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';
import { api } from '$lib/api';

export const load: PageServerLoad = async ({ params, locals, parent }) => {
	// The session token this pro-gated preview needs is already available server-side, so fetch it
	// here (streamed, not awaited) instead of shipping a blank shell that only starts fetching once
	// the client hydrates and an effect fires — same 403-if-not-pro gate as before, just server-side.
	const [player, { session }, parentData] = await Promise.all([
		api.players.get(params.id).catch(() => null),
		locals.safeGetSession(),
		parent()
	]);
	if (!player) error(404, 'Player not found');

	const preview =
		parentData.isPro && session
			? api.players.matchPreview(params.id, params.matchId, session.access_token).catch(() => null)
			: Promise.resolve(null);

	return {
		player,
		matchId: params.matchId,
		streamed: { preview }
	};
};

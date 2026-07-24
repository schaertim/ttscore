import { api } from '$lib/api';
import { error } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import { authedKtor } from '$lib/server/ktor';
import { followAction, unfollowAction, setNotifyAction } from '$lib/server/followActions';

export const load: PageServerLoad = async ({ params, locals }) => {
	const id = params.id;

	const [group, standings, matches] = await Promise.all([
		api.groups.get(id).catch(() => null),
		api.groups.standings(id).catch(() => null),
		api.groups.matches(id).catch(() => null)
	]);

	if (!group || !standings) error(404, 'Group not found');

	// Fallback team list for groups scraped from a "participating teams" table rather than a
	// standings table (e.g. cup/final/playoff groups) — only needed when there's no standings.
	const teams = standings.length === 0 ? await api.groups.teams(id).catch(() => []) : [];

	const { session } = await locals.safeGetSession();

	let following = false;
	let followId: string | null = null;
	let notify = false;

	if (session) {
		const ktor = authedKtor(session.access_token);
		const followRes = await ktor
			.get(`/follows/check?targetType=division_group&targetId=${id}`)
			.catch(() => null);
		if (followRes && followRes.ok) {
			const d = await followRes.json();
			following = d.following;
			followId = d.followId ?? null;
			notify = d.notify;
		}
	}

	return { group, standings, teams, matches: matches ?? [], following, followId, notify };
};

export const actions: Actions = {
	follow: followAction,
	unfollow: unfollowAction,
	setNotify: setNotifyAction
};

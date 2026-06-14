<script lang="ts">
	import type { FollowResponse } from '$lib/api';
	import { api } from '$lib/api';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import GameCard from '$lib/components/GameCard.svelte';
	import MatchCard from '$lib/components/MatchCard.svelte';
	import { CaretRightIcon } from 'phosphor-svelte';
	import { formatName } from '$lib/utils';

	interface Props {
		follow: FollowResponse;
	}

	let { follow }: Props = $props();

	const hrefBase =
		follow.targetType === 'division_group'
			? `/groups/${follow.targetId}`
			: follow.targetType === 'team'
				? `/teams/${follow.targetId}`
				: `/players/${follow.targetId}`;

	const isPlayer = follow.targetType === 'player';

	const playerMatchesPromise = isPlayer
		? api.players.matches(follow.targetId).then((ms) => ms.slice(0, 3))
		: Promise.resolve([] as Awaited<ReturnType<typeof api.players.matches>>);

	const teamMatchesPromise = !isPlayer
		? (follow.targetType === 'team'
				? api.teams.matches(follow.targetId)
				: api.groups.matches(follow.targetId)
			).then((ms) => ms.slice(0, 3))
		: Promise.resolve([] as Awaited<ReturnType<typeof api.teams.matches>>);
</script>

<div class="space-y-3 rounded-xl border border-border bg-card p-4">
	<a href={hrefBase} class="flex items-center justify-between">
		<p class="text-sm font-semibold hover:underline">{follow.targetType === 'player' ? formatName(follow.targetName) : follow.targetName}</p>
		<CaretRightIcon size="16" class="shrink-0 text-muted-foreground" />
	</a>

	{#if isPlayer}
		{#await playerMatchesPromise}
			<div class="space-y-3">
				{#each [1, 2] as i (i)}
					<Skeleton class="h-12 w-full rounded-xl" />
				{/each}
			</div>
		{:then games}
			{#if games.length === 0}
				<p class="text-xs text-muted-foreground">No recent activity.</p>
			{:else}
				<div class="space-y-3">
					{#each games as game (game.gameId)}
						<GameCard mode="player" {game} />
					{/each}
				</div>
			{/if}
		{/await}
	{:else}
		{#await teamMatchesPromise}
			<div class="space-y-3">
				{#each [1, 2] as i (i)}
					<Skeleton class="h-12 w-full rounded-xl" />
				{/each}
			</div>
		{:then matches}
			{#if matches.length === 0}
				<p class="text-xs text-muted-foreground">No recent activity.</p>
			{:else}
				<div class="space-y-3">
					{#each matches as match (match.id)}
						<MatchCard {match} />
					{/each}
				</div>
			{/if}
		{/await}
	{/if}
</div>

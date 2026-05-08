<script lang="ts">
	import type { Player, PlayerGame, LeagueContext, FavoriteResponse } from '$lib/api';
	import HomeHero from '$lib/components/home/HomeHero.svelte';
	import HomeQuicklinks from '$lib/components/home/HomeQuicklinks.svelte';
	import FavoritesFeed from '$lib/components/home/FavoritesFeed.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import GameCard from '$lib/components/GameCard.svelte';
	import { ClockCounterClockwise } from 'phosphor-svelte';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';

	interface Props {
		player: Player;
		streamed: {
			recentMatches: Promise<PlayerGame[]>;
			leagueContext: Promise<LeagueContext | null>;
			favorites: Promise<FavoriteResponse[]>;
		};
	}

	let { player, streamed }: Props = $props();

	const recentMatchCount = streamed.recentMatches.then((ms) => ms.length);
</script>

<div class="space-y-6 pb-4">
	<!-- Hero: greeting + klass badge + ELO + sparkline -->
	<HomeHero {player} recentMatches={streamed.recentMatches} />

	<!-- Quicklinks: My Profile, My Matches, My League, My Team -->
	<section class="space-y-3">
		<HomeQuicklinks
			{player}
			leagueContext={streamed.leagueContext}
			{recentMatchCount}
		/>
	</section>

	<!-- Recent Results (last 3) -->
	<section class="space-y-3">
		<SectionLabel label="Recent Results" icon={ClockCounterClockwise} class="px-1" />
		{#await streamed.recentMatches}
			<div class="space-y-2">
				{#each [1, 2, 3] as i (i)}
					<Skeleton class="h-16 w-full rounded-xl" />
				{/each}
			</div>
		{:then matches}
			{@const played = matches.filter((m) => m.status !== 'SCHEDULED')}
			{#if played.length === 0}
				<p class="px-1 text-sm text-muted-foreground">No recent games found.</p>
			{:else}
				<div class="space-y-2">
					{#each played.slice(0, 3) as game (game.gameId)}
						<GameCard mode="player" {game} />
					{/each}
				</div>
				{#if played.length > 3}
					<a
						href="/players/{player.id}/games"
						class="block pt-1 text-center text-xs font-bold tracking-widest text-muted-foreground uppercase hover:text-foreground"
					>
						Show Full History →
					</a>
				{/if}
			{/if}
		{/await}
	</section>

	<!-- Favorites feed -->
	<FavoritesFeed favorites={streamed.favorites} />
</div>

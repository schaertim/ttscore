<script lang="ts">
	import type { Player, PlayerGame, LeagueContext, FavoriteResponse } from '$lib/api';
	import HomeHero from '$lib/components/home/HomeHero.svelte';
	import HomeQuicklinks from '$lib/components/home/HomeQuicklinks.svelte';
	import FavoritesFeed from '$lib/components/home/FavoritesFeed.svelte';

	interface Props {
		player: Player;
		streamed: {
			recentMatches: Promise<PlayerGame[]>;
			leagueContext: Promise<LeagueContext | null>;
			favorites: Promise<FavoriteResponse[]>;
		};
	}

	let { player, streamed }: Props = $props();
</script>

<div class="space-y-6 pb-4">
	<!-- Hero: greeting + klass badge + ELO + sparkline -->
	<HomeHero {player} recentMatches={streamed.recentMatches} />

	<!-- Quicklinks: My Profile, My Matches, My League, My Team -->
	<section class="space-y-3">
		<HomeQuicklinks
			{player}
			leagueContext={streamed.leagueContext}
		/>
	</section>

	<!-- Favorites feed -->
	<FavoritesFeed favorites={streamed.favorites} />
</div>

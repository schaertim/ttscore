<script lang="ts">
	import type { Player, PlayerGame, EloEntry, LeagueContext, FavoriteResponse } from '$lib/api';
	import HomeHero from '$lib/components/home/HomeHero.svelte';
	import HomeQuicklinks from '$lib/components/home/HomeQuicklinks.svelte';
	import FavoritesFeed from '$lib/components/home/FavoritesFeed.svelte';

	interface Props {
		player: Player;
		streamed: {
			recentMatches: Promise<PlayerGame[]>;
			eloHistory: Promise<EloEntry[]>;
			leagueContext: Promise<LeagueContext | null>;
			favorites: Promise<FavoriteResponse[]>;
		};
	}

	let { player, streamed }: Props = $props();
</script>

<div class="space-y-6">
	<!-- Hero: greeting + class badge + ELO + sparkline -->
	<HomeHero {player} recentMatches={streamed.recentMatches} eloHistory={streamed.eloHistory} />

	<HomeQuicklinks {player} leagueContext={streamed.leagueContext} />

	<!-- Favorites feed -->
	<FavoritesFeed favorites={streamed.favorites} />
</div>

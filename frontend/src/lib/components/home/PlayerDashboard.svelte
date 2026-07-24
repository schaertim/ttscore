<script lang="ts">
	import type { Player, PlayerGame, EloEntry, LeagueContext, FollowResponse } from '$lib/api';
	import type { FeedEvent } from '$lib/feed';
	import HomeHero from '$lib/components/home/HomeHero.svelte';
	import HomeQuicklinks from '$lib/components/home/HomeQuicklinks.svelte';
	import FollowFeed from '$lib/components/home/FollowFeed.svelte';

	interface Props {
		player: Player;
		streamed: {
			recentMatches: Promise<PlayerGame[]>;
			eloHistory: Promise<EloEntry[]>;
			leagueContext: Promise<LeagueContext | null>;
			follows: Promise<FollowResponse[]>;
			feedEvents: Promise<FeedEvent[]>;
		};
	}

	let { player, streamed }: Props = $props();
</script>

<div class="space-y-6">
	<!-- Hero: greeting + class badge + ELO + sparkline -->
	<HomeHero {player} recentMatches={streamed.recentMatches} eloHistory={streamed.eloHistory} />

	<HomeQuicklinks {player} leagueContext={streamed.leagueContext} />

	<!-- Follow feed -->
	<FollowFeed follows={streamed.follows} feedEvents={streamed.feedEvents} />
</div>

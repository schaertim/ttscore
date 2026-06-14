<script lang="ts">
	import type { PageData } from './$types';
	import BackButton from '$lib/components/BackButton.svelte';
	import GameCard from '$lib/components/GameCard.svelte';

	let { data }: { data: PageData } = $props();
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<BackButton class="" />

		<div class="flex items-center gap-4">
			<p class="min-w-0 flex-1 text-right text-2xl font-black wrap-break-word">{data.match.homeTeam}</p>

			<span class="shrink-0 text-5xl font-black tracking-tight tabular-nums">
				{data.match.homeScore ?? '?'}<span class="mx-1 text-muted-foreground/30">:</span>{data.match
					.awayScore ?? '?'}
			</span>

			<p class="min-w-0 flex-1 text-left text-2xl font-black wrap-break-word">{data.match.awayTeam}</p>
		</div>
	</header>

	<div class="space-y-3">
		{#each data.match.games as game, i (i)}
			<GameCard mode="match" {game} />
		{/each}
	</div>
</div>

<script lang="ts">
	import type { PageData } from './$types';
	import BackButton from '$lib/components/BackButton.svelte';
	import GameCard from '$lib/components/GameCard.svelte';

	let { data }: { data: PageData } = $props();
</script>

<div class="mx-auto max-w-2xl p-4 pb-20">
	<header class="mb-6 px-1">
		<BackButton />
	</header>

	<div class="mb-10 px-1">
		<div class="flex items-center gap-4">
			<p class="min-w-0 flex-1 text-right text-3xl font-bold break-words">{data.match.homeTeam}</p>

			<span class="shrink-0 text-5xl font-black tracking-tighter tabular-nums">
				{data.match.homeScore ?? '?'}<span class="mx-1 text-muted-foreground/30">:</span>{data.match
					.awayScore ?? '?'}
			</span>

			<p class="min-w-0 flex-1 text-left text-3xl font-bold break-words">{data.match.awayTeam}</p>
		</div>
	</div>

	<section class="space-y-3">
		<h2 class="px-1 text-[10px] font-black tracking-[0.2em] text-muted-foreground uppercase">
			Game Breakdown
		</h2>

		<div class="space-y-4">
			{#each data.match.games as game}
				<GameCard mode="match" {game} />
			{/each}
		</div>
	</section>
</div>

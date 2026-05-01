<script lang="ts">
	import type { PageData } from './$types';
	import * as Card from '$lib/components/ui/card/index.js';
	import BackButton from '$lib/components/BackButton.svelte';
	import { klassColors } from '$lib/utils';

	let { data }: { data: PageData } = $props();

	function lastName(name: string | null): string {
		if (!name) return '—';
		return name.split(' ').at(-1) ?? name;
	}

	function displayName(name1: string | null, name2: string | null): string {
		if (!name2) return name1 ?? '—';
		return `${lastName(name1)} / ${lastName(name2)}`;
	}



</script>

<div class="p-4 pb-20 max-w-2xl mx-auto">
	<header class="px-1 mb-6">
		<BackButton />
	</header>

	<div class="px-1 mb-10">
		<div class="flex items-center gap-4">
			<p class="flex-1 min-w-0 text-3xl font-bold text-right break-words">{data.match.homeTeam}</p>

			<span class="shrink-0 text-5xl font-black tabular-nums tracking-tighter">
				{data.match.homeScore ?? '?'}<span class="text-muted-foreground/30 mx-1">:</span>{data.match.awayScore ?? '?'}
			</span>

			<p class="flex-1 min-w-0 text-3xl font-bold text-left break-words">{data.match.awayTeam}</p>
		</div>
	</div>

	<section class="space-y-3">
		<h2 class="text-[10px] font-black uppercase tracking-[0.2em] text-muted-foreground px-1">
			Game Breakdown
		</h2>

		<div class="space-y-4">
			{#each data.match.games as game}
				{@const homeWon = game.result === 'HOME'}
				{@const awayWon = game.result === 'AWAY'}
				<Card.Root>
					<div class="px-5 space-y-3">
						<!-- Header -->
						<p class="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">
							Game #{game.orderInMatch} · {game.gameType}
						</p>

						<!-- Players (stacked) + score (right) -->
						<div class="flex items-center gap-4">
							<!-- Stacked player rows -->
							<div class="flex-1 min-w-0 space-y-1.5">
								<!-- Home player(s) -->
								<div class="flex items-center gap-1.5 min-w-0">
									{#if game.homePlayer2Name}
										<!-- Doubles: [LastName Badge] / [LastName Badge] -->
										<div class="flex items-center gap-1 min-w-0 shrink">
											<svelte:element this={game.homePlayerId ? 'a' : 'span'}
												href={game.homePlayerId ? `/players/${game.homePlayerId}` : undefined}
												class="truncate {homeWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground'} {game.homePlayerId ? 'hover:underline' : ''}"
											>{lastName(game.homePlayerName)}</svelte:element>
											{#if game.homePlayerKlass}
												<span class="shrink-0 text-[10px] font-black px-1.5 py-0.5 rounded tracking-wide {klassColors(game.homePlayerKlass)}">{game.homePlayerKlass}</span>
											{/if}
										</div>
										<span class="shrink-0 text-muted-foreground/40">/</span>
										<div class="flex items-center gap-1 min-w-0 shrink">
											<svelte:element this={game.homePlayer2Id ? 'a' : 'span'}
												href={game.homePlayer2Id ? `/players/${game.homePlayer2Id}` : undefined}
												class="truncate {homeWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground'} {game.homePlayer2Id ? 'hover:underline' : ''}"
											>{lastName(game.homePlayer2Name)}</svelte:element>
											{#if game.homePlayer2Klass}
												<span class="shrink-0 text-[10px] font-black px-1.5 py-0.5 rounded tracking-wide {klassColors(game.homePlayer2Klass)}">{game.homePlayer2Klass}</span>
											{/if}
										</div>
									{:else}
										<!-- Singles -->
										<svelte:element this={game.homePlayerId ? 'a' : 'span'}
											href={game.homePlayerId ? `/players/${game.homePlayerId}` : undefined}
											class="min-w-0 truncate {homeWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground'} {game.homePlayerId ? 'hover:underline' : ''}"
										>{game.homePlayerName ?? '—'}</svelte:element>
										{#if game.homePlayerKlass}
											<span class="shrink-0 text-[10px] font-black px-1.5 py-0.5 rounded tracking-wide {klassColors(game.homePlayerKlass)}">{game.homePlayerKlass}</span>
										{/if}
									{/if}
								</div>
								<!-- Away player(s) -->
								<div class="flex items-center gap-1.5 min-w-0">
									{#if game.awayPlayer2Name}
										<!-- Doubles: [LastName Badge] / [LastName Badge] -->
										<div class="flex items-center gap-1 min-w-0 shrink">
											<svelte:element this={game.awayPlayerId ? 'a' : 'span'}
												href={game.awayPlayerId ? `/players/${game.awayPlayerId}` : undefined}
												class="truncate {awayWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground'} {game.awayPlayerId ? 'hover:underline' : ''}"
											>{lastName(game.awayPlayerName)}</svelte:element>
											{#if game.awayPlayerKlass}
												<span class="shrink-0 text-[10px] font-black px-1.5 py-0.5 rounded tracking-wide {klassColors(game.awayPlayerKlass)}">{game.awayPlayerKlass}</span>
											{/if}
										</div>
										<span class="shrink-0 text-muted-foreground/40">/</span>
										<div class="flex items-center gap-1 min-w-0 shrink">
											<svelte:element this={game.awayPlayer2Id ? 'a' : 'span'}
												href={game.awayPlayer2Id ? `/players/${game.awayPlayer2Id}` : undefined}
												class="truncate {awayWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground'} {game.awayPlayer2Id ? 'hover:underline' : ''}"
											>{lastName(game.awayPlayer2Name)}</svelte:element>
											{#if game.awayPlayer2Klass}
												<span class="shrink-0 text-[10px] font-black px-1.5 py-0.5 rounded tracking-wide {klassColors(game.awayPlayer2Klass)}">{game.awayPlayer2Klass}</span>
											{/if}
										</div>
									{:else}
										<!-- Singles -->
										<svelte:element this={game.awayPlayerId ? 'a' : 'span'}
											href={game.awayPlayerId ? `/players/${game.awayPlayerId}` : undefined}
											class="min-w-0 truncate {awayWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground'} {game.awayPlayerId ? 'hover:underline' : ''}"
										>{game.awayPlayerName ?? '—'}</svelte:element>
										{#if game.awayPlayerKlass}
											<span class="shrink-0 text-[10px] font-black px-1.5 py-0.5 rounded tracking-wide {klassColors(game.awayPlayerKlass)}">{game.awayPlayerKlass}</span>
										{/if}
									{/if}
								</div>
							</div>

							<!-- Score -->
							<p class="shrink-0 text-3xl font-black tabular-nums text-muted-foreground">
								{game.homeSets ?? 0}:{game.awaySets ?? 0}
							</p>
						</div>

						{#if game.sets && game.sets.length > 0}
							<div class="flex flex-wrap gap-1.5">
								{#each game.sets as set}
									<span class="text-xs tabular-nums font-medium px-2.5 py-1 rounded
									             bg-muted text-muted-foreground">
										{set.homePoints}:{set.awayPoints}
									</span>
								{/each}
							</div>
						{/if}
					</div>
				</Card.Root>
			{/each}
		</div>
	</section>
</div>

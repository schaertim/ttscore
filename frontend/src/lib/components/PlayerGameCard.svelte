<script lang="ts">
	import type { PlayerGame } from '$lib/api';
	import { cn, formatName } from '$lib/utils';
	import { dayMonth } from '$lib/date';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import * as Card from '$lib/components/ui/card/index.js';
	import { Separator } from '$lib/components/ui/separator/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { locale } from 'svelte-i18n';

	interface Props {
		/** A game seen from one player's perspective (opponent, own sets first, ELO delta). */
		game: PlayerGame;
		/** Shows a skeleton in place of a missing ELO delta while the on-demand sync backfills it. */
		syncing?: boolean;
	}

	let { game, syncing = false }: Props = $props();

	function formatDelta(delta: number | null): string {
		if (delta == null) return '';
		const abs = Math.abs(delta).toFixed(1);
		return delta >= 0 ? `+${abs}` : `-${abs}`;
	}

	const playerSets = $derived(
		game.playerSide === 'home' ? (game.homeSets ?? 0) : (game.awaySets ?? 0)
	);
	const opponentSets = $derived(
		game.playerSide === 'home' ? (game.awaySets ?? 0) : (game.homeSets ?? 0)
	);
	const scoreColor = $derived(
		playerSets === opponentSets
			? 'text-muted-foreground'
			: playerSets > opponentSets
				? 'text-win'
				: 'text-loss'
	);
</script>

<svelte:element
	this={game.matchId ? 'a' : 'div'}
	href={game.matchId ? `/matches/${game.matchId}` : undefined}
	class="block"
>
	<Card.Root class="py-3 transition-colors {game.matchId ? 'hover:bg-accent' : ''}">
		<div class="flex items-stretch gap-3 px-4">
			<!-- left: meta + opponent + sets -->
			<div class="flex min-w-0 flex-1 flex-col gap-2">
				<div
					class="flex min-w-0 items-center gap-1.5 text-2xs font-semibold tracking-widest text-muted-foreground uppercase"
				>
					<span class="shrink-0">{dayMonth(game.playedAt, $locale) ?? '—'}</span>
					<Separator
						orientation="vertical"
						class="bg-muted-foreground/40 data-[orientation=vertical]:h-2.5"
					/>
					<span class="truncate">{game.competitionName ?? '—'}</span>
				</div>
				<div class="flex min-w-0 items-center gap-2">
					{#if game.opponentId}
						<a
							href="/players/{game.opponentId}"
							class="min-w-0 truncate text-lg font-normal hover:underline"
							onclick={(e) => e.stopPropagation()}>{formatName(game.opponentName)}</a
						>
					{:else}
						<span class="min-w-0 truncate text-lg font-normal">{formatName(game.opponentName)}</span
						>
					{/if}
					<ClassBadge classification={game.opponentClassification} />
				</div>
				{#if game.sets.length > 0}
					<div class="flex flex-wrap gap-1">
						{#each game.sets as set, i (i)}
							{@const playerWonSet =
								game.playerSide === 'home'
									? set.homePoints > set.awayPoints
									: set.awayPoints > set.homePoints}
							<span
								class={cn(
									'rounded px-2 py-1 font-mono text-2xs font-semibold tracking-tight tabular-nums',
									playerWonSet ? 'bg-win/15 text-win' : 'bg-loss/15 text-loss'
								)}
							>
								{game.playerSide === 'home'
									? `${set.homePoints}:${set.awayPoints}`
									: `${set.awayPoints}:${set.homePoints}`}
							</span>
						{/each}
					</div>
				{/if}
			</div>
			<!-- divider -->
			<Separator
				orientation="vertical"
				class="self-stretch bg-muted-foreground/50 data-[orientation=vertical]:h-auto m-1"
			/>
			<!-- right: score + ELO centered as group -->
			<div class="flex w-11 shrink-0 flex-col items-center justify-center gap-1">
				<p
					class={cn(
						'font-mono text-2xl leading-none font-black tracking-tighter tabular-nums',
						scoreColor
					)}
				>
					{playerSets}:{opponentSets}
				</p>
				{#if game.eloDelta != null}
					<span
						class="font-mono text-2xs font-semibold whitespace-nowrap text-muted-foreground"
						class:italic={game.eloDeltaProvisional}
						title={game.eloDeltaProvisional ? 'Provisional — not yet officially rated' : undefined}
					>
						{formatDelta(game.eloDelta)} ELO
					</span>
				{:else if syncing}
					<Skeleton class="h-3 w-8 rounded" />
				{/if}
			</div>
		</div>
	</Card.Root>
</svelte:element>

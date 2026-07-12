<script lang="ts">
	import type { Game } from '$lib/api';
	import { cn, formatName } from '$lib/utils';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import * as Card from '$lib/components/ui/card/index.js';
	import { _ } from 'svelte-i18n';

	interface Props {
		/** A neutral match game: both sides shown, winner emphasised. */
		game: Game;
	}

	let { game }: Props = $props();

	// For compact doubles display: return the family name portion (all but last word).
	// Storage format is "Lastname Firstname", so family name = everything except the last word.
	function lastName(name: string | null): string {
		if (!name) return '—';
		const parts = name.trim().split(/\s+/);
		return parts.length > 1 ? parts.slice(0, -1).join(' ') : parts[0];
	}

	type SidePlayers = {
		playerId: string | null;
		playerName: string | null;
		playerClassification: string | null;
		player2Id: string | null;
		player2Name: string | null;
		player2Classification: string | null;
	};

	const homeWon = $derived(game.result === 'HOME');
	const awayWon = $derived(game.result === 'AWAY');
</script>

{#snippet playerName(
	id: string | null,
	name: string | null,
	classification: string | null,
	won: boolean,
	compact: boolean
)}
	{@const nameClass = cn(
		'min-w-0 truncate text-base',
		won ? 'font-semibold text-foreground' : 'font-normal text-muted-foreground'
	)}
	{#if id}
		<a href="/players/{id}" class={cn(nameClass, 'hover:underline')}>
			{compact ? lastName(name) : formatName(name)}
		</a>
	{:else}
		<span class={nameClass}>{compact ? lastName(name) : formatName(name)}</span>
	{/if}
	<ClassBadge {classification} />
{/snippet}

{#snippet gameSide(side: SidePlayers, won: boolean)}
	<div class="flex min-w-0 items-center gap-2">
		{#if side.player2Name}
			<div class="flex min-w-0 shrink items-center gap-1">
				{@render playerName(side.playerId, side.playerName, side.playerClassification, won, true)}
			</div>
			<span class="shrink-0 text-muted-foreground/40">/</span>
			<div class="flex min-w-0 shrink items-center gap-1">
				{@render playerName(
					side.player2Id,
					side.player2Name,
					side.player2Classification,
					won,
					true
				)}
			</div>
		{:else}
			{@render playerName(side.playerId, side.playerName, side.playerClassification, won, false)}
		{/if}
	</div>
{/snippet}

<Card.Root class="py-3">
	<div class="flex items-stretch gap-3 px-4">
		<!-- left: game label + home player + away player + sets -->
		<div class="flex min-w-0 flex-1 flex-col gap-2">
			<p class="text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
				{$_('match.game_label', { values: { number: game.orderInMatch } })} · {game.gameType ===
				'SINGLES'
					? $_('match.singles')
					: $_('match.doubles')}
			</p>

			{@render gameSide(
				{
					playerId: game.homePlayerId,
					playerName: game.homePlayerName,
					playerClassification: game.homePlayerClassification,
					player2Id: game.homePlayer2Id,
					player2Name: game.homePlayer2Name,
					player2Classification: game.homePlayer2Classification
				},
				homeWon
			)}

			{@render gameSide(
				{
					playerId: game.awayPlayerId,
					playerName: game.awayPlayerName,
					playerClassification: game.awayPlayerClassification,
					player2Id: game.awayPlayer2Id,
					player2Name: game.awayPlayer2Name,
					player2Classification: game.awayPlayer2Classification
				},
				awayWon
			)}

			{#if game.sets && game.sets.length > 0}
				<div class="flex flex-wrap gap-1">
					{#each game.sets as set, i (i)}
						<span
							class="rounded bg-muted px-2 py-1 font-mono text-xs font-semibold tracking-tight text-muted-foreground tabular-nums"
						>
							{set.homePoints}:{set.awayPoints}
						</span>
					{/each}
				</div>
			{/if}
		</div>
		<!-- divider -->
		<div class="w-px shrink-0 self-stretch bg-border"></div>
		<!-- right: score, vertically centered -->
		<div class="flex w-11 shrink-0 flex-col items-center justify-center">
			<p class="font-mono text-3xl font-black tracking-tighter text-muted-foreground tabular-nums">
				{game.homeSets ?? 0}:{game.awaySets ?? 0}
			</p>
		</div>
	</div>
</Card.Root>

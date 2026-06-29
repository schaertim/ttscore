<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { PlayerSeasonStats } from '$lib/api';
	import * as Card from '$lib/components/ui/card/index.js';

	interface Props {
		stats: PlayerSeasonStats;
	}

	let { stats }: Props = $props();

	const C = 2 * Math.PI * 27;

	const setWinRate = $derived(
		stats.setsWon + stats.setsLost > 0
			? Math.round((stats.setsWon / (stats.setsWon + stats.setsLost)) * 100)
			: 0
	);
	const deuceRate = $derived(
		stats.deuceSetsTotal > 0 ? Math.round((stats.deuceSetsWon / stats.deuceSetsTotal) * 100) : 0
	);
</script>

{#snippet gauge(value: number, label: string, sub: string | null, color: string)}
	<div class="flex flex-1 flex-col items-center">
		<svg viewBox="0 0 70 70" class="size-[58px]">
			<circle cx="35" cy="35" r="27" fill="none" stroke="var(--color-muted)" stroke-width="7" />
			<circle
				cx="35"
				cy="35"
				r="27"
				fill="none"
				stroke={color}
				stroke-width="7"
				stroke-linecap="round"
				stroke-dasharray="{(value / 100) * C} {C}"
				transform="rotate(-90 35 35)"
			/>
			<text x="35" y="40" text-anchor="middle" class="fill-foreground text-[15px] font-bold"
				>{value}%</text
			>
		</svg>
		<span class="mt-1 text-xs text-muted-foreground">{label}</span>
		{#if sub}<span class="text-2xs text-muted-foreground/70">{sub}</span>{/if}
	</div>
{/snippet}

<Card.Root class="gap-4 p-5">
	<div class="flex gap-3">
		{@render gauge(setWinRate, $_('stats.set_win_rate'), null, 'var(--color-win)')}
		{@render gauge(
			deuceRate,
			$_('stats.deuce_sets'),
			$_('stats.deuce_sub'),
			'var(--color-primary)'
		)}
	</div>
	<div class="flex gap-3">
		<div class="flex-1 rounded-xl bg-muted/40 p-3 text-center">
			<div class="text-base font-black tabular-nums">
				<span class="text-win">{stats.tightGameWins}</span><span class="text-muted-foreground"
					>–</span
				><span class="text-loss">{stats.tightGames - stats.tightGameWins}</span>
			</div>
			<div class="mt-0.5 text-2xs text-muted-foreground">{$_('stats.tight_games')}</div>
		</div>
		<div class="flex-1 rounded-xl bg-muted/40 p-3 text-center">
			<div class="text-base font-black text-win tabular-nums">{stats.comebackWins}</div>
			<div class="mt-0.5 text-2xs text-muted-foreground">{$_('stats.comeback_wins')}</div>
		</div>
	</div>
</Card.Root>

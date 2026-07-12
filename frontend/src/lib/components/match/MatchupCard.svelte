<script lang="ts">
	import type { PreviewMatchup } from '$lib/api';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import { comparePlayers } from '$lib/h2h.svelte';
	import { formatShortName } from '$lib/utils';
	import { _ } from 'svelte-i18n';

	interface Props {
		matchup: PreviewMatchup;
	}

	let { matchup }: Props = $props();

	// ELO-implied edge for the home player (null when either rating is unknown).
	const homePct = $derived(
		matchup.homeWinProbability != null ? Math.round(matchup.homeWinProbability * 100) : null
	);
</script>

<button
	type="button"
	onclick={() => comparePlayers(matchup.homePlayer.id, matchup.awayPlayer.id)}
	class="w-full rounded-xl border border-border bg-card px-4 py-3 text-left transition-colors hover:bg-accent"
>
	<div class="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
		<!-- Home player -->
		<div class="min-w-0">
			<div class="flex min-w-0 items-center gap-1.5">
				<p class="min-w-0 truncate text-sm font-semibold">
					{formatShortName(matchup.homePlayer.fullName)}
				</p>
				<ClassBadge classification={matchup.homePlayer.classification} />
			</div>
			<span class="mt-1 block font-mono text-2xs text-muted-foreground tabular-nums">
				{#if matchup.homePlayer.elo != null}{matchup.homePlayer.elo} ELO{:else}&nbsp;{/if}
			</span>
		</div>

		<!-- Direct record -->
		<div class="flex flex-col items-center px-2">
			{#if matchup.meetings > 0}
				<div
					class="flex items-baseline gap-1 font-mono text-lg leading-none font-black tabular-nums"
				>
					<span class="text-win">{matchup.homeWins}</span>
					<span class="font-normal text-muted-foreground/40">–</span>
					<span class="text-loss">{matchup.awayWins}</span>
				</div>
				<span class="mt-1 text-2xs text-muted-foreground">
					{$_('preview.meetings', { values: { count: matchup.meetings } })}
				</span>
			{:else}
				<span class="text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
					{$_('preview.first_meeting')}
				</span>
			{/if}
		</div>

		<!-- Away player -->
		<div class="min-w-0 text-right">
			<div class="flex min-w-0 items-center justify-end gap-1.5">
				<ClassBadge classification={matchup.awayPlayer.classification} />
				<p class="min-w-0 truncate text-sm font-semibold">
					{formatShortName(matchup.awayPlayer.fullName)}
				</p>
			</div>
			<span class="mt-0.5 block font-mono text-2xs text-muted-foreground tabular-nums">
				{#if matchup.awayPlayer.elo != null}{matchup.awayPlayer.elo} ELO{:else}&nbsp;{/if}
			</span>
		</div>
	</div>

	{#if homePct != null}
		<div class="mt-2.5 flex items-center gap-2">
			<span class="w-8 shrink-0 font-mono text-2xs text-muted-foreground tabular-nums"
				>{homePct}%</span
			>
			<div class="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
				<div class="h-full rounded-full bg-primary" style="width: {homePct}%"></div>
			</div>
			<span class="w-8 shrink-0 text-right font-mono text-2xs text-muted-foreground tabular-nums">
				{100 - homePct}%
			</span>
		</div>
	{/if}
</button>

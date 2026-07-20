<script lang="ts">
	import type { Match } from '$lib/api';
	import { cn } from '$lib/utils';
	import { dateNumeric } from '$lib/date';
	import { HouseLineIcon, TrainIcon } from 'phosphor-svelte';
	import { Separator } from '$lib/components/ui/separator/index.js';
	import { _, locale } from 'svelte-i18n';

	interface Props {
		match: Match;
		/** When set, shows only the opponent + H/A icon instead of both team names */
		perspectiveTeam?: string;
		/** Overrides the link target (defaults to the match detail page). */
		href?: string;
	}

	let { match, perspectiveTeam, href }: Props = $props();

	type Result = 'win' | 'loss' | 'completed' | null;

	const hasScore = $derived(match.homeScore != null && match.awayScore != null);

	const result = $derived.by<Result>(() => {
		if (!hasScore || match.status !== 'COMPLETED') return null;
		const h = match.homeScore!;
		const a = match.awayScore!;
		if (!perspectiveTeam || h === a) return 'completed';
		const perspectiveWins =
			(perspectiveTeam === match.homeTeam && h > a) ||
			(perspectiveTeam === match.awayTeam && a > h);
		return perspectiveWins ? 'win' : 'loss';
	});

	const scoreClass = $derived(
		result === 'win'
			? 'text-win bg-win/15 border-win/30'
			: result === 'loss'
				? 'text-loss bg-loss/15 border-loss/30'
				: result != null
					? 'text-muted-foreground bg-muted border-border'
					: 'text-muted-foreground border-border'
	);

	const isHome = $derived(perspectiveTeam === match.homeTeam);
	const opponent = $derived(perspectiveTeam ? (isHome ? match.awayTeam : match.homeTeam) : null);
</script>

<a
	href={href ?? `/matches/${match.id}`}
	class="group flex items-center justify-between rounded-xl border
	       border-border bg-card px-4 py-3 transition-colors hover:bg-accent"
>
	<div class="flex min-w-0 flex-col gap-1">
		<span
			class="flex items-center gap-1.5 text-2xs font-semibold tracking-widest text-muted-foreground uppercase"
		>
			{#if match.round}
				<span>Rd {match.round}</span>
				<Separator
					orientation="vertical"
					class="bg-muted-foreground/40 data-[orientation=vertical]:h-2.5"
				/>
			{/if}
			<span>{dateNumeric(match.playedAt, $locale) ?? $_('common.tbd')}</span>
		</span>

		{#if perspectiveTeam}
			<div class="flex min-w-0 items-center gap-2">
				{#if isHome}
					<HouseLineIcon weight="fill" size={16} class="text-muted-foreground/60" />
				{:else}
					<TrainIcon weight="fill" size={16} class="text-muted-foreground/60" />
				{/if}
				<span class="truncate text-sm font-semibold">{opponent}</span>
			</div>
		{:else}
			<div class="flex min-w-0 items-center gap-2 text-sm">
				<span class="truncate font-semibold">{match.homeTeam}</span>
				<span class="shrink-0 text-xs text-muted-foreground">vs</span>
				<span class="truncate font-semibold">{match.awayTeam}</span>
			</div>
		{/if}
	</div>

	<span
		class={cn(
			'min-w-12 shrink-0 rounded-md border px-1 py-1 text-center font-mono text-sm font-semibold tracking-tight tabular-nums',
			scoreClass
		)}
	>
		{#if hasScore}
			{match.homeScore}:{match.awayScore}
		{:else}
			–:–
		{/if}
	</span>
</a>

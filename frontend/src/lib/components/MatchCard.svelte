<script lang="ts">
	import type { Match } from '$lib/api';
	import { cn } from '$lib/utils';
	import { HouseLine, Train } from 'phosphor-svelte';

	interface Props {
		match: Match;
		/** When set, shows only the opponent + H/A icon instead of both team names */
		perspectiveTeam?: string;
	}

	let { match, perspectiveTeam }: Props = $props();

	const hasScore = match.homeScore != null && match.awayScore != null;

	type Result = 'win' | 'loss' | 'completed' | null;

	const result: Result = (() => {
		if (!hasScore || match.status !== 'COMPLETED') return null;
		const h = match.homeScore!;
		const a = match.awayScore!;
		if (!perspectiveTeam || h === a) return 'completed';
		const perspectiveWins =
			(perspectiveTeam === match.homeTeam && h > a) ||
			(perspectiveTeam === match.awayTeam && a > h);
		return perspectiveWins ? 'win' : 'loss';
	})();

	const scoreClass =
		result === 'win'
			? 'text-win bg-win/15'
			: result === 'loss'
				? 'text-loss bg-loss/15'
				: result != null
					? 'text-muted-foreground bg-muted'
					: 'text-muted-foreground';

	const isHome = perspectiveTeam === match.homeTeam;
	const opponent = perspectiveTeam ? (isHome ? match.awayTeam : match.homeTeam) : null;

	function formatDate(dateStr: string | null): string {
		if (!dateStr) return 'TBD';
		return new Date(dateStr).toLocaleDateString('de-CH', {
			day: '2-digit',
			month: '2-digit',
			year: '2-digit'
		});
	}
</script>

<a
	href="/matches/{match.id}"
	class="group flex items-center justify-between rounded-xl border
	       border-border bg-card px-4 py-3 transition-colors hover:bg-accent"
>
	<div class="flex min-w-0 flex-col gap-0.5">
		<span class="text-[10px] font-medium tracking-widest text-muted-foreground uppercase">
			{#if match.round}Rd {match.round} ·
			{/if}{formatDate(match.playedAt)}
		</span>

		{#if perspectiveTeam}
			<div class="flex min-w-0 items-center gap-1.5">
				{#if isHome}
					<HouseLine weight="fill" class="h-4 w-4 shrink-0 text-muted-foreground/60" />
				{:else}
					<Train weight="fill" class="h-4 w-4 shrink-0 text-muted-foreground/60" />
				{/if}
				<span class="truncate text-sm font-semibold">{opponent}</span>
			</div>
		{:else}
			<div class="flex min-w-0 items-center gap-1.5 text-sm">
				<span class="truncate font-medium">{match.homeTeam}</span>
				<span class="flex-shrink-0 text-xs text-muted-foreground">vs</span>
				<span class="truncate font-medium">{match.awayTeam}</span>
			</div>
		{/if}
	</div>

	<span
		class={cn(
			'ml-3 min-w-[3rem] shrink-0 rounded-md border px-2.5 py-1 text-center text-sm font-black tabular-nums',
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

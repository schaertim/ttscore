<script lang="ts">
	import type { Match } from '$lib/api';
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
		result === 'win'  ? 'text-win border-win/30 bg-win/10' :
		result === 'loss' ? 'text-loss border-loss/30 bg-loss/10' :
		result != null    ? 'text-muted-foreground border-muted-foreground/25 bg-muted' :
		                    'text-muted-foreground border-border';

	const isHome = perspectiveTeam === match.homeTeam;
	const opponent = perspectiveTeam
		? (isHome ? match.awayTeam : match.homeTeam)
		: null;

	function formatDate(dateStr: string | null): string {
		if (!dateStr) return 'TBD';
		return new Date(dateStr).toLocaleDateString('de-CH', {
			day: '2-digit',
			month: '2-digit',
			year: '2-digit',
		});
	}
</script>

<a
	href="/matches/{match.id}"
	class="flex items-center justify-between px-4 py-3 rounded-xl
	       bg-card border border-border hover:bg-accent transition-colors group"
>
	<div class="flex flex-col gap-0.5 min-w-0">
		<span class="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">
			{#if match.round}Rd {match.round} · {/if}{formatDate(match.playedAt)}
		</span>

		{#if perspectiveTeam}
			<!-- Perspective mode: icon inline with opponent name -->
			<div class="flex items-center gap-1.5 min-w-0">
				{#if isHome}
					<HouseLine weight="fill" class="w-4 h-4 shrink-0 text-muted-foreground/60" />
				{:else}
					<Train weight="fill" class="w-4 h-4 shrink-0 text-muted-foreground/60" />
				{/if}
				<span class="text-sm font-semibold truncate">{opponent}</span>
			</div>
		{:else}
			<!-- Neutral mode: both team names -->
			<div class="flex items-center gap-1.5 text-sm min-w-0">
				<span class="font-medium truncate">{match.homeTeam}</span>
				<span class="text-muted-foreground flex-shrink-0 text-xs">vs</span>
				<span class="font-medium truncate">{match.awayTeam}</span>
			</div>
		{/if}
	</div>

	<span class="shrink-0 ml-3 min-w-[3rem] text-center text-sm font-black tabular-nums px-2.5 py-1
	             rounded-md border {scoreClass}">
		{#if hasScore}
			{match.homeScore}:{match.awayScore}
		{:else}
			–:–
		{/if}
	</span>
</a>

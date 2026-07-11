<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { PlayerSeasonStats } from '$lib/api';
	import BestWinTile from '$lib/components/BestWinTile.svelte';
	import StatTile from '$lib/components/StatTile.svelte';
	import ScoreLine from '$lib/components/ScoreLine.svelte';

	interface Props {
		stats: PlayerSeasonStats;
	}

	let { stats }: Props = $props();
</script>

<div class="space-y-3">
	{#if stats.bestWinOpponentName}
		<BestWinTile
			label={$_('stats.best_win')}
			opponentId={stats.bestWinOpponentId}
			opponentName={stats.bestWinOpponentName}
			opponentClass={stats.bestWinOpponentClass}
		/>
	{/if}

	<div class="grid grid-cols-2 gap-3">
		<StatTile label={$_('stats.longest_streak')}>
			<ScoreLine segments={[{ value: stats.longestWinStreak, tone: 'win' }]} />
		</StatTile>
		<StatTile label={$_('stats.current_streak')} value={stats.currentWinStreak} />
	</div>
</div>

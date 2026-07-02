<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { PlayerSeasonStats } from '$lib/api';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import StatTile from '$lib/components/StatTile.svelte';
	import ScoreLine from '$lib/components/ScoreLine.svelte';
	import { formatName } from '$lib/utils';

	interface Props {
		stats: PlayerSeasonStats;
	}

	let { stats }: Props = $props();
</script>

<div class="space-y-3">
	{#if stats.bestWinOpponentName}
		<StatTile label={$_('stats.best_win')}>
			<p class="flex items-center gap-1.5 truncate text-base font-semibold">
				<span class="truncate">{formatName(stats.bestWinOpponentName)}</span>
				<ClassBadge classification={stats.bestWinOpponentClass} />
			</p>
		</StatTile>
	{/if}

	<div class="grid grid-cols-2 gap-3">
		<StatTile label={$_('stats.longest_streak')}>
			<ScoreLine segments={[{ value: stats.longestWinStreak, tone: 'win' }]} />
		</StatTile>
		<StatTile label={$_('stats.current_streak')} value={stats.currentWinStreak} />
	</div>
</div>

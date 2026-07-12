<script lang="ts">
	import { _ } from 'svelte-i18n';
	import StatTile from '$lib/components/StatTile.svelte';
	import ScoreLine from '$lib/components/ScoreLine.svelte';

	interface Props {
		/** Label for the total-games tile (e.g. "Games" for a season, "Matches" for a career). */
		totalLabel: string;
		total: number;
		wins: number;
		losses: number;
	}

	let { totalLabel, total, wins, losses }: Props = $props();

	const winPct = $derived(total > 0 ? Math.round((wins / total) * 100) : 0);
</script>

<!-- The total · W–L record · win-rate stat trio shared by the season and career tabs. -->
<div class="grid grid-cols-3 gap-3">
	<StatTile label={totalLabel} labelPosition="bottom" align="center" value={total} />
	<StatTile label={$_('stats.record')} labelPosition="bottom" align="center">
		<ScoreLine
			segments={[
				{ value: wins, tone: 'win' },
				{ value: losses, tone: 'loss' }
			]}
		/>
	</StatTile>
	<StatTile
		label={$_('stats.win_rate')}
		labelPosition="bottom"
		align="center"
		value={`${winPct}%`}
	/>
</div>

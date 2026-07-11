<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { H2HGame } from '$lib/api';
	import StatTile from '$lib/components/StatTile.svelte';
	import ScoreLine from '$lib/components/ScoreLine.svelte';

	interface Props {
		/** All H2H games, oriented so the a-side belongs to the left player. */
		games: H2HGame[];
	}

	let { games }: Props = $props();

	const setsRecord = $derived.by(() => {
		let left = 0,
			right = 0;
		for (const g of games) {
			left += g.aSets ?? 0;
			right += g.bSets ?? 0;
		}
		return { left, right };
	});

	const pointsRecord = $derived.by(() => {
		let left = 0,
			right = 0;
		for (const g of games) {
			for (const s of g.sets) {
				left += s.homePoints;
				right += s.awayPoints;
			}
		}
		return { left, right };
	});

	// Green when this side leads, red when it trails, muted when tied — matching the
	// win/loss coloring used across the app.
	function sideTone(value: number, other: number): 'win' | 'loss' | 'neutral' {
		if (value > other) return 'win';
		if (value < other) return 'loss';
		return 'neutral';
	}
</script>

{#if setsRecord.left > 0 || setsRecord.right > 0}
	<section class="grid grid-cols-2 gap-3">
		<StatTile label={$_('h2h.sets')} labelPosition="bottom" align="center">
			<ScoreLine
				segments={[
					{ value: setsRecord.left, tone: sideTone(setsRecord.left, setsRecord.right) },
					{ value: setsRecord.right, tone: sideTone(setsRecord.right, setsRecord.left) }
				]}
			/>
		</StatTile>
		{#if pointsRecord.left > 0 || pointsRecord.right > 0}
			<StatTile label={$_('h2h.points')} labelPosition="bottom" align="center">
				<ScoreLine
					segments={[
						{ value: pointsRecord.left, tone: sideTone(pointsRecord.left, pointsRecord.right) },
						{ value: pointsRecord.right, tone: sideTone(pointsRecord.right, pointsRecord.left) }
					]}
				/>
			</StatTile>
		{/if}
	</section>
{/if}

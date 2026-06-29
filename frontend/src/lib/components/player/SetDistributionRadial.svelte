<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { SetScoreBucket } from '$lib/api';
	import * as Card from '$lib/components/ui/card/index.js';
	import * as Chart from '$lib/components/ui/chart/index.js';
	import { PieChart, Arc, Text } from 'layerchart';

	interface Props {
		buckets: SetScoreBucket[];
	}

	let { buckets }: Props = $props();

	type Segment = { label: string; count: number; color: string; isWin: boolean };

	const segments = $derived.by<Segment[]>(() => {
		const wins = buckets.filter((b) => b.playerSets > b.opponentSets);
		const losses = buckets.filter((b) => b.playerSets < b.opponentSets);
		return buckets.map((b) => {
			const isWin = b.playerSets > b.opponentSets;
			const group = isWin ? wins : losses;
			const idx = group.indexOf(b);
			const n = group.length;
			// Wins: 100% (most dominant, darkest) → 40% (closest, lightest)
			// Losses: 40% (closest, lightest) → 100% (most decisive, darkest)
			const op =
				n <= 1 ? 75 : isWin
					? Math.round(100 - (idx / (n - 1)) * 60)
					: Math.round(40 + (idx / (n - 1)) * 60);
			const base = isWin ? 'var(--color-win)' : 'var(--color-loss)';
			return {
				label: `${b.playerSets}:${b.opponentSets}`,
				count: b.count,
				isWin,
				color: `color-mix(in oklch, ${base}, transparent ${100 - op}%)`
			};
		});
	});

	const total = $derived(buckets.reduce((sum, b) => sum + b.count, 0));

	const chartConfig = $derived(
		Object.fromEntries(
			segments.map((s) => [s.label, { label: s.label, color: s.color }])
		) satisfies Chart.ChartConfig
	);
</script>

<Card.Root class="flex flex-col">
	<Card.Content class="flex-1">
		<Chart.Container config={chartConfig} class="mx-auto aspect-square max-h-[250px]">
			<PieChart
				data={segments}
				key="label"
				value="count"
				c="color"
				innerRadius={60}
				padding={29}
				props={{ pie: { sort: null, motion: 'tween' } }}
				cornerRadius={4}
			>
				{#snippet arc({ props, context })}
					{@const startA = props.startAngle ?? 0}
					{@const endA = props.endAngle ?? 0}
					{@const midAngle = (startA + endA) / 2}
					{@const spanAngle = Math.abs(endA - startA)}
					{@const outerR = Math.min(context.width, context.height) / 2}
					{@const midR = (60 + outerR) / 2}
					{@const lx = midR * Math.sin(midAngle)}
					{@const ly = -midR * Math.cos(midAngle)}
					{@const seg = props.data as Segment}
					<Arc {...props} />
					{#if spanAngle > 0.35}
						<text
							x={lx}
							y={ly}
							text-anchor="middle"
							dominant-baseline="middle"
							font-size="11"
							font-weight="700"
							fill="white"
							class="pointer-events-none select-none"
						>{seg.label}</text>
					{/if}
				{/snippet}
				{#snippet aboveMarks()}
					<Text
						value={String(total)}
						textAnchor="middle"
						verticalAnchor="end"
						class="fill-foreground text-2xl! font-black"
						dy={-2}
					/>
					<Text
						value={$_('stats.games')}
						textAnchor="middle"
						verticalAnchor="start"
						class="fill-muted-foreground! text-xs"
						dy={2}
					/>
				{/snippet}
				{#snippet tooltip()}
					<Chart.Tooltip />
				{/snippet}
			</PieChart>
		</Chart.Container>
	</Card.Content>
</Card.Root>

<script lang="ts">
	import { AnnotationLine, Highlight, LinearGradient, LineChart, Spline } from 'layerchart';
	import { scaleLinear, scaleUtc } from 'd3-scale';
	import { curveMonotoneX } from 'd3-shape';
	import * as Chart from '$lib/components/ui/chart/index.js';
	import {
		ELO_THRESHOLDS,
		bandGradientStops,
		classColorVar,
		type GradientContext
	} from '$lib/utils';
	import { _, locale } from 'svelte-i18n';
	import type { EloEntry } from '$lib/api';

	interface EloChartSeries {
		label: string;
		/** Stroke colour (CSS var or value). */
		color: string;
		history: EloEntry[];
	}

	interface Props {
		series: EloChartSeries[];
	}

	let { series }: Props = $props();

	const PAD = 30;

	const parsed = $derived(
		series.map((s) => ({
			label: s.label,
			color: s.color,
			points: s.history
				.map((e) => ({ date: new Date(e.recordedAt), value: e.eloValue }))
				.sort((a, b) => a.date.getTime() - b.date.getTime())
		}))
	);

	const drawable = $derived(parsed.filter((s) => s.points.length >= 2));

	const allValues = $derived(parsed.flatMap((s) => s.points.map((p) => p.value)));
	const yMin = $derived(allValues.length ? Math.min(...allValues) - PAD : 0);
	const yMax = $derived(allValues.length ? Math.max(...allValues) + PAD : 0);

	// Union of all recorded timestamps, ascending.
	const dates = $derived.by(() => {
		// eslint-disable-next-line svelte/prefer-svelte-reactivity -- local temp, discarded after the computation
		const seen = new Map<number, Date>();
		for (const s of parsed) for (const p of s.points) seen.set(p.date.getTime(), p.date);
		return [...seen.values()].sort((a, b) => a.getTime() - b.getTime());
	});

	// One row per union date; each series carries its last known ELO forward (ELO is
	// constant between games), and is null before that player's first record.
	const rows = $derived.by(() =>
		dates.map((date) => {
			const row: Record<string, number | Date | null> = { date };
			parsed.forEach((s, i) => {
				let v: number | null = null;
				for (const p of s.points) {
					if (p.date.getTime() <= date.getTime()) v = p.value;
					else break;
				}
				row[`p${i}`] = v;
			});
			return row;
		})
	);

	const chartSeries = $derived(
		parsed.map((s, i) => ({ key: `p${i}`, label: s.label, color: s.color }))
	);
	const chartConfig = $derived(
		Object.fromEntries(
			parsed.map((s, i) => [`p${i}`, { label: s.label, color: s.color }])
		) as Chart.ChartConfig
	);

	const visibleThresholds = $derived(ELO_THRESHOLDS.filter(([elo]) => elo > yMin && elo < yMax));

	// The left line is always vivid, additional lines (the right player in H2H) are muted — the
	// same fixed left/right convention used for the solid per-player colours.
	const singleSeries = $derived(chartSeries.length === 1);
	const faded = (c: string) => `color-mix(in srgb, ${c} 40%, transparent)`;

	// The class an ELO falls into (ELO_THRESHOLDS is descending by min), and its class colour.
	function eloClass(elo: number): string {
		for (const [min, cls] of ELO_THRESHOLDS) if (elo >= min) return cls;
		return ELO_THRESHOLDS[ELO_THRESHOLDS.length - 1][1];
	}
	const eloColor = (elo: number, muted: boolean) => {
		const c = classColorVar(eloClass(elo));
		return muted ? faded(c) : c;
	};

	// The line takes each class band's colour by height — colour switches hard wherever the
	// class letter changes across the visible ELO range.
	function gradientStops(context: GradientContext, muted: boolean): [number, string][] {
		return bandGradientStops(
			context,
			yMin,
			yMax,
			eloColor(yMax, muted),
			ELO_THRESHOLDS.map(([min]) => ({ boundary: min, color: eloColor(min - 1, muted) }))
		);
	}
</script>

{#if drawable.length === 0}
	<div class="flex h-full items-center justify-center">
		<p class="text-sm text-muted-foreground">{$_('player.no_elo_data')}</p>
	</div>
{:else}
	<Chart.Container config={chartConfig} class="aspect-auto h-full">
		<LineChart
			data={rows}
			x="date"
			xScale={scaleUtc()}
			yScale={scaleLinear()}
			yDomain={[yMin, yMax]}
			axis="x"
			padding={{ right: 20, bottom: 20, left: 10 }}
			grid={false}
			rule={false}
			series={chartSeries}
			props={{
				xAxis: {
					format: (v: Date) =>
						v.toLocaleDateString($locale ?? 'de', { month: 'short', year: '2-digit' })
				}
			}}
		>
			{#snippet marks({ context })}
				{#each context.series.visibleSeries as s (s.key)}
					<LinearGradient
						stops={gradientStops(context, s.key !== chartSeries[0]?.key)}
						units="userSpaceOnUse"
						vertical
					>
						{#snippet children({ gradient })}
							<Spline
								seriesKey={s.key}
								stroke={gradient}
								strokeWidth={2}
								curve={curveMonotoneX}
								defined={(d: Record<string, number | Date | null>) => d[s.key] != null}
							/>
						{/snippet}
					</LinearGradient>
				{/each}
				{#each visibleThresholds as [elo, label] (elo)}
					<AnnotationLine
						y={elo}
						{label}
						labelPlacement="right"
						props={{
							line: {
								style: 'stroke: var(--foreground); stroke-opacity: 0.3',
								'stroke-dasharray': '4 3'
							},
							label: { class: 'fill-foreground/30 text-2xs' }
						}}
					/>
				{/each}
			{/snippet}

			{#snippet highlight({ context })}
				{#if singleSeries && context.tooltip.data}
					<Highlight
						lines
						points={{ r: 4, fill: eloColor(context.y(context.tooltip.data), false) }}
					/>
				{:else}
					<Highlight lines points={{ r: 4 }} />
				{/if}
			{/snippet}

			{#snippet tooltip({ context })}
				<Chart.Tooltip
					hideLabel
					color={singleSeries && context.tooltip.data
						? eloColor(context.y(context.tooltip.data), false)
						: undefined}
				/>
			{/snippet}
		</LineChart>
	</Chart.Container>
{/if}

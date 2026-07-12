<script lang="ts">
	import type { PreviewFixture } from '$lib/api';
	import FormPills from '$lib/components/FormPills.svelte';
	import { HouseIcon, AirplaneTiltIcon } from 'phosphor-svelte';
	import { _, locale } from 'svelte-i18n';
	import { relativeTime, dateWeekday } from '$lib/date';

	interface Props {
		fixture: PreviewFixture;
	}

	let { fixture }: Props = $props();

	// Relative "in 2 days" / "in 5 hours" countdown to throw-off; empty once it's under way.
	function countdown(dateStr: string | null): string {
		if (!dateStr || new Date(dateStr).getTime() <= Date.now()) return '';
		return relativeTime(dateStr, $locale);
	}

	// Home's share of a mirrored comparison bar, centred at 50%. The split reflects the gap between
	// the two values relative to their combined magnitude, so it behaves sensibly for signed
	// quantities (games diff) — including when both sides are negative. Equal values read 50/50.
	function homeShare(home: number, away: number): number {
		const spread = Math.abs(home) + Math.abs(away);
		if (spread === 0) return 50;
		return Math.round(50 + (50 * (home - away)) / spread);
	}

	function signed(d: number): string {
		return d > 0 ? `+${d}` : `${d}`;
	}
</script>

<!-- Fixture hero: teams, standings context, and season-so-far comparison in one card -->
<header class="space-y-5 rounded-2xl border border-border/50 bg-card p-5">
	<div
		class="flex w-full items-center justify-between gap-2 text-2xs font-semibold tracking-widest text-muted-foreground uppercase"
	>
		<a href="/groups/{fixture.groupId}" class="min-w-0 truncate hover:text-foreground">
			{fixture.groupName}
		</a>
		<span class="shrink-0">{dateWeekday(fixture.playedAt, $locale) ?? $_('common.tbd')}</span>
	</div>

	<div class="grid grid-cols-[1fr_auto_1fr] items-stretch gap-3">
		<a
			href="/teams/{fixture.home.teamId}"
			class="flex min-w-0 flex-col items-end gap-1.5 text-right"
		>
			<p class="w-full min-w-0 truncate text-lg leading-tight font-black">
				{fixture.home.teamName}
			</p>
			<div class="flex items-center gap-2.5">
				{#if fixture.home.position > 0}
					<span
						class="shrink-0 font-mono text-4xl leading-none font-black text-muted-foreground/15 tabular-nums"
					>
						#{fixture.home.position}
					</span>
				{/if}
				<div class="flex flex-col items-end gap-1.5">
					<FormPills form={fixture.home.form} class="justify-end" />
					<span
						class="inline-flex items-center gap-1 text-2xs font-semibold tracking-widest text-muted-foreground uppercase"
					>
						{$_('preview.home')}
						<HouseIcon size={12} weight="fill" />
					</span>
				</div>
			</div>
		</a>

		<div class="flex flex-col items-center justify-center gap-1.5 px-1">
			<span class="text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
				{$_('preview.vs')}
			</span>
			{#if countdown(fixture.playedAt)}
				<span
					class="rounded-full border border-border px-2 py-0.5 text-2xs font-semibold whitespace-nowrap"
				>
					{countdown(fixture.playedAt)}
				</span>
			{/if}
		</div>

		<a
			href="/teams/{fixture.away.teamId}"
			class="flex min-w-0 flex-col items-start gap-1.5 text-left"
		>
			<p class="w-full min-w-0 truncate text-lg leading-tight font-black">
				{fixture.away.teamName}
			</p>
			<div class="flex items-center gap-2.5">
				<div class="flex flex-col items-start gap-1.5">
					<FormPills form={fixture.away.form} class="justify-start" />
					<span
						class="inline-flex items-center gap-1 text-2xs font-semibold tracking-widest text-muted-foreground uppercase"
					>
						{$_('preview.away')}
						<AirplaneTiltIcon size={12} weight="fill" />
					</span>
				</div>
				{#if fixture.away.position > 0}
					<span
						class="shrink-0 font-mono text-4xl leading-none font-black text-muted-foreground/15 tabular-nums"
					>
						#{fixture.away.position}
					</span>
				{/if}
			</div>
		</a>
	</div>

	<div class="space-y-3">
		{@render compareBar(
			$_('group.points'),
			fixture.home.points,
			fixture.away.points,
			`${fixture.home.points}`,
			`${fixture.away.points}`
		)}
		{@render compareBar(
			$_('preview.diff'),
			fixture.home.gamesDiff,
			fixture.away.gamesDiff,
			signed(fixture.home.gamesDiff),
			signed(fixture.away.gamesDiff)
		)}
	</div>
</header>

{#snippet compareBar(
	label: string,
	home: number,
	away: number,
	homeLabel: string,
	awayLabel: string
)}
	{@const pct = homeShare(home, away)}
	<div class="space-y-1">
		<div class="flex items-baseline justify-between gap-2">
			<span class="font-mono text-sm font-black tabular-nums">{homeLabel}</span>
			<span class="text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
				{label}
			</span>
			<span class="font-mono text-sm font-black tabular-nums">{awayLabel}</span>
		</div>
		<div class="flex h-1.5 gap-0.5">
			<div class="h-full rounded-full bg-primary" style="width: {pct}%"></div>
			<div class="h-full flex-1 rounded-full bg-muted-foreground/25"></div>
		</div>
	</div>
{/snippet}

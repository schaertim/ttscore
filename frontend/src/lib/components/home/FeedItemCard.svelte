<script lang="ts">
	import type { FeedItem } from './feed-types';
	import { cn, classificationColors } from '$lib/utils';
	import { relativeTime } from '$lib/date';
	import { Separator } from '$lib/components/ui/separator/index.js';
	import { _, locale } from 'svelte-i18n';
	import {
		UserIcon,
		UsersThreeIcon,
		TrophyIcon,
		TrendUpIcon,
		TrendDownIcon,
		ThumbsUpIcon,
		ThumbsDownIcon,
		HandshakeIcon,
		MedalIcon,
		PingPongIcon,
		ClockIcon,
		ArrowRightIcon
	} from 'phosphor-svelte';

	interface Props {
		entityType: 'player' | 'team' | 'division_group';
		entityName: string;
		entityHref: string;
		item: FeedItem;
	}

	let { entityType, entityName, entityHref, item }: Props = $props();

	// ── Entity icon ──────────────────────────────────────────────────────────────

	const EntityIcon = $derived(
		entityType === 'player' ? UserIcon : entityType === 'team' ? UsersThreeIcon : TrophyIcon
	);

	// ── Event badge ───────────────────────────────────────────────────────────────

	type BadgeSpec = { icon: typeof TrendUpIcon; bg: string; text: string };

	function getBadge(i: FeedItem): BadgeSpec {
		if (i.kind === 'player_match') {
			if (i.result === 'WIN') return { icon: TrendUpIcon, bg: 'bg-win/15', text: 'text-win' };
			if (i.result === 'LOSS') return { icon: TrendDownIcon, bg: 'bg-loss/15', text: 'text-loss' };
			return { icon: HandshakeIcon, bg: 'bg-muted', text: 'text-muted-foreground' };
		}
		if (i.kind === 'team_match') {
			if (i.result === 'WIN') return { icon: ThumbsUpIcon, bg: 'bg-win/15', text: 'text-win' };
			if (i.result === 'LOSS') return { icon: ThumbsDownIcon, bg: 'bg-loss/15', text: 'text-loss' };
			return { icon: HandshakeIcon, bg: 'bg-muted', text: 'text-muted-foreground' };
		}
		if (i.kind === 'class_change') {
			const kc = classificationColors(i.to).split(' ');
			const bg = kc.find((c) => c.startsWith('bg-')) ?? 'bg-muted';
			const text = kc.find((c) => c.startsWith('text-')) ?? 'text-muted-foreground';
			return { icon: MedalIcon, bg, text };
		}
		if (i.kind === 'upcoming_match') {
			return { icon: ClockIcon, bg: 'bg-primary/15', text: 'text-primary' };
		}
		return { icon: PingPongIcon, bg: 'bg-muted', text: 'text-muted-foreground' };
	}

	function getDescription(i: FeedItem): string {
		switch (i.kind) {
			case 'player_match': {
				const prefix =
					i.result === 'WIN'
						? $_('feed.won')
						: i.result === 'LOSS'
							? $_('feed.lost')
							: $_('feed.drew');
				return `${prefix} ${i.matchScore} ${$_('feed.vs')} ${i.opponentTeam}`;
			}
			case 'class_change':
				return i.direction === 'UP' ? $_('feed.promoted') : $_('feed.relegated');
			case 'team_match': {
				const prefix =
					i.result === 'WIN'
						? $_('feed.won')
						: i.result === 'LOSS'
							? $_('feed.lost')
							: $_('feed.drew');
				return `${prefix} ${i.score} ${$_('feed.vs')} ${i.opponent}`;
			}
			case 'group_match':
				return `${i.homeTeam} ${i.score} ${i.awayTeam}`;
			case 'upcoming_match':
				return `${i.homeTeam} ${$_('feed.vs')} ${i.awayTeam}`;
		}
	}

	// Relative time label. Past events read "2 days ago"; an upcoming fixture counts down ("in 5h").
	function displayTime(i: FeedItem): string | null {
		if (i.kind === 'class_change') return relativeTime(i.effectiveDate, $locale);
		if (i.kind === 'upcoming_match') {
			// Only a genuinely future throw-off gets a countdown; anything past reads nothing.
			if (!i.playedAt || new Date(i.playedAt).getTime() <= Date.now()) return null;
			return relativeTime(i.playedAt, $locale);
		}
		return i.playedAt ? relativeTime(i.playedAt, $locale) : null;
	}

	const badge = $derived(getBadge(item));
	const description = $derived(getDescription(item));
	const timestamp = $derived(displayTime(item));
	const isUpcoming = $derived(item.kind === 'upcoming_match');
</script>

<a
	href={entityHref}
	class="group flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 hover:bg-accent"
>
	<div class="flex h-9 w-7 shrink-0 items-center justify-center rounded-lg">
		<EntityIcon size={20} class="text-muted-foreground" />
	</div>

	<!-- Center: name + description + timestamp -->
	<div class="min-w-0 flex-1">
		<div class="flex items-baseline justify-between gap-2">
			<p class="truncate text-sm font-semibold">{entityName}</p>
			{#if timestamp}
				<p class="shrink-0 text-xs text-muted-foreground">{timestamp}</p>
			{/if}
		</div>
		<p class="flex min-w-0 items-center gap-1.5 truncate text-xs text-muted-foreground">
			{#if isUpcoming}
				<span class="shrink-0">{$_('feed.upcoming')}</span>
				<Separator
					orientation="vertical"
					class="bg-muted-foreground/40 data-[orientation=vertical]:h-2.5"
				/>
			{/if}
			{#if item.kind === 'class_change'}
				<span class="shrink-0">{description}</span>
				<span class="shrink-0">{item.from}</span>
				<ArrowRightIcon size={12} weight="bold" class="shrink-0" />
				<span class="truncate">{item.to}</span>
			{:else}
				<span class="truncate">{description}</span>
			{/if}
		</p>
	</div>

	<!-- Right: event type badge -->
	<div
		class={cn(
			'flex h-8 w-8 shrink-0 items-center justify-center rounded-sm ring-1 ring-transparent transition-all',
			badge.bg,
			badge.bg === 'bg-muted' && 'group-hover:ring-border'
		)}
	>
		<badge.icon size={16} class={badge.text} weight="bold" />
	</div>
</a>

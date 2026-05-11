<script lang="ts">
	import type { FeedItem } from './feed-types';
	import { timeAgo } from '$lib/utils';
	import {
		User,
		UsersThree,
		Trophy,
		TrendUp,
		TrendDown,
		Minus,
		ArrowUp,
		ArrowDown
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
		entityType === 'player' ? User : entityType === 'team' ? UsersThree : Trophy
	);

	// ── Event badge ───────────────────────────────────────────────────────────────

	type BadgeSpec = { icon: typeof TrendUp; bg: string; text: string };

	function getBadge(i: FeedItem): BadgeSpec {
		if (i.kind === 'player_match' || i.kind === 'team_match') {
			if (i.result === 'WIN') return { icon: TrendUp, bg: 'bg-win/15', text: 'text-win' };
			if (i.result === 'LOSS') return { icon: TrendDown, bg: 'bg-loss/15', text: 'text-loss' };
			return { icon: Minus, bg: 'bg-amber-500/15', text: 'text-amber-500' };
		}
		if (i.kind === 'class_change') {
			return i.direction === 'UP'
				? { icon: ArrowUp, bg: 'bg-win/15', text: 'text-win' }
				: { icon: ArrowDown, bg: 'bg-loss/15', text: 'text-loss' };
		}
		return { icon: Minus, bg: 'bg-muted', text: 'text-muted-foreground' };
	}

	function getDescription(i: FeedItem): string {
		switch (i.kind) {
			case 'player_match': {
				const prefix = i.result === 'WIN' ? 'Won' : i.result === 'LOSS' ? 'Lost' : 'Drew';
				return `${prefix} ${i.matchScore} vs. ${i.opponentTeam}`;
			}
			case 'class_change':
				return i.direction === 'UP'
					? `Promoted ${i.from} → ${i.to}`
					: `Relegated ${i.from} → ${i.to}`;
			case 'team_match': {
				const prefix = i.result === 'WIN' ? 'Won' : i.result === 'LOSS' ? 'Lost' : 'Drew';
				return `${prefix} ${i.score} vs. ${i.opponent}`;
			}
			case 'group_latest':
				return `${i.homeTeam} ${i.score} ${i.awayTeam}`;
		}
	}

	function getTimestamp(i: FeedItem): string | null {
		if (i.kind === 'class_change') return null;
		return i.playedAt ?? null;
	}

	const badge = $derived(getBadge(item));
	const description = $derived(getDescription(item));
	const timestamp = $derived(getTimestamp(item));
</script>

<a href={entityHref} class="flex items-center gap-3 px-4 py-3.5 hover:bg-accent">
	<!-- Left: entity type icon -->
	<div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-muted">
		<EntityIcon size={20} class="text-muted-foreground" />
	</div>

	<!-- Center: name + description + timestamp -->
	<div class="min-w-0 flex-1">
		<div class="flex items-baseline justify-between gap-2">
			<p class="truncate text-sm font-semibold">{entityName}</p>
			{#if timestamp}
				<p class="shrink-0 text-xs text-muted-foreground">{timeAgo(timestamp)}</p>
			{/if}
		</div>
		<p class="truncate text-xs text-muted-foreground">{description}</p>
	</div>

	<!-- Right: event type badge -->
	<div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl {badge.bg}">
		<badge.icon size={16} class={badge.text} weight="bold" />
	</div>
</a>

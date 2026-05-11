<script lang="ts">
	import type { FeedItem } from './feed-types';
	import { cn, klassColors, timeAgo } from '$lib/utils';
	import {
		User,
		UsersThree,
		Trophy,
		TrendUp,
		TrendDown,
		ThumbsUp,
		ThumbsDown,
		Handshake,
		Medal,
		PingPong
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
		if (i.kind === 'player_match') {
			const wins = parseInt(i.matchScore.split('–')[0]);
			const up = !isNaN(wins) && wins >= 2;
			return up
				? { icon: TrendUp, bg: 'bg-win/15', text: 'text-win' }
				: { icon: TrendDown, bg: 'bg-loss/15', text: 'text-loss' };
		}
		if (i.kind === 'team_match') {
			if (i.result === 'WIN') return { icon: ThumbsUp, bg: 'bg-win/15', text: 'text-win' };
			if (i.result === 'LOSS') return { icon: ThumbsDown, bg: 'bg-loss/15', text: 'text-loss' };
			return { icon: Handshake, bg: 'bg-muted', text: 'text-muted-foreground' };
		}
		if (i.kind === 'class_change') {
			const kc = klassColors(i.to).split(' ');
			const bg = kc.find((c) => c.startsWith('bg-')) ?? 'bg-muted';
			const text = kc.find((c) => c.startsWith('text-')) ?? 'text-muted-foreground';
			return { icon: Medal, bg, text };
		}
		return { icon: PingPong, bg: 'bg-muted', text: 'text-muted-foreground' };
	}

	function getDescription(i: FeedItem): string {
		switch (i.kind) {
			case 'player_match': {
				const [myStr, oppStr] = i.matchScore.split('–');
				const myWins = parseInt(myStr);
				const oppWins = parseInt(oppStr);
				const won = !isNaN(myWins) && myWins >= 2;
				const higher = !isNaN(myWins) && !isNaN(oppWins) ? Math.max(myWins, oppWins) : null;
				const prefix = won ? 'Won' : 'Lost';
				return `${prefix}${higher !== null ? ` ${higher}` : ''} vs. ${i.opponentTeam}`;
			}
			case 'class_change':
				return i.direction === 'UP'
					? `Promoted ${i.from} → ${i.to}`
					: `Relegated ${i.from} → ${i.to}`;
			case 'team_match': {
				const prefix = i.result === 'WIN' ? 'Won' : i.result === 'LOSS' ? 'Lost' : 'Drew';
				return `${prefix} ${i.score} vs. ${i.opponent}`;
			}
			case 'group_match':
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

<a href={entityHref} class="group flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 hover:bg-accent">
	<div class="flex h-9 w-7 shrink-0 items-center justify-center rounded-lg">
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
	<div class={cn('flex h-8 w-8 shrink-0 items-center justify-center rounded-sm ring-1 ring-transparent transition-all', badge.bg, badge.bg === 'bg-muted' && 'group-hover:ring-border')}>
		<badge.icon size={16} class={badge.text} weight="bold" />
	</div>
</a>

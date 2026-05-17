<script lang="ts">
	import type { Player, LeagueContext } from '$lib/api';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import QuicklinkRow from '$lib/components/home/QuicklinkRow.svelte';
	import { UserIcon, ListBulletsIcon, TrophyIcon, UsersThreeIcon, ClockCounterClockwiseIcon, LinkIcon } from 'phosphor-svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';

	interface Props {
		player: Player;
		leagueContext: Promise<LeagueContext | null>;
	}

	let { player, leagueContext }: Props = $props();
</script>

<section class="space-y-3">
<SectionLabel label="Quick Links" icon={LinkIcon} />
<div class="divide-y divide-border overflow-hidden rounded-xl border border-border bg-card">
	<QuicklinkRow
		href="/players/{player.id}"
		icon={UserIcon}
		label="My Profile"
		sublabel="Stats, ELO history & more"
	/>
	<QuicklinkRow
		href="/players/{player.id}/games"
		icon={ClockCounterClockwiseIcon}
		label="My Games"
		sublabel="Your game history"
	/>
	{#await leagueContext}
		<div class="flex items-center gap-3 px-4 py-3">
			<Skeleton class="h-9 w-9 shrink-0 rounded-lg" />
			<div class="flex-1 space-y-1.5">
				<Skeleton class="h-3.5 w-24 rounded" />
				<Skeleton class="h-3 w-36 rounded" />
			</div>
		</div>
		<div class="flex items-center gap-3 px-4 py-3">
			<Skeleton class="h-9 w-9 shrink-0 rounded-lg" />
			<div class="flex-1 space-y-1.5">
				<Skeleton class="h-3.5 w-24 rounded" />
				<Skeleton class="h-3 w-36 rounded" />
			</div>
		</div>
	{:then ctx}
		{#if ctx}
			<QuicklinkRow
				href="/groups/{ctx.groupId}"
				icon={TrophyIcon}
				label="My League"
				sublabel={ctx.groupName}
			/>
			<QuicklinkRow
				href="/teams/{ctx.teamId}"
				icon={UsersThreeIcon}
				label="My Team"
				sublabel={ctx.scheduledMatchCount > 0
					? `${ctx.teamName} · ${ctx.scheduledMatchCount} upcoming`
					: ctx.teamName}
			/>
		{/if}
	{/await}
</div>
</section>

<script lang="ts">
	import type { Player, LeagueContext } from '$lib/api';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import QuicklinkRow from '$lib/components/home/QuicklinkRow.svelte';
	import { User, ListBullets, Trophy, UsersThree, ClockCounterClockwise } from 'phosphor-svelte';

	interface Props {
		player: Player;
		leagueContext: Promise<LeagueContext | null>;
	}

	let { player, leagueContext }: Props = $props();

</script>

<div class="space-y-2">
	<!-- My Profile -->
	<QuicklinkRow
		href="/players/{player.id}"
		icon={User}
		label="My Profile"
		sublabel="Stats, ELO history & more"
	/>

	<!-- My Matches -->
	<QuicklinkRow
		href="/players/{player.id}/games"
		icon={ClockCounterClockwise}
		label="My Matches"
		sublabel="Your game history"
	/>

	<!-- My League (async) -->
	{#await leagueContext}
		<Skeleton class="h-[60px] w-full rounded-xl" />
	{:then ctx}
		{#if ctx}
			<QuicklinkRow
				href="/groups/{ctx.groupId}"
				icon={Trophy}
				label="My League"
				sublabel={ctx.groupName}
			/>
		{/if}
	{/await}

	<!-- My Team (async) -->
	{#await leagueContext}
		<Skeleton class="h-[60px] w-full rounded-xl" />
	{:then ctx}
		{#if ctx}
			<QuicklinkRow
				href="/teams/{ctx.teamId}"
				icon={UsersThree}
				label="My Team"
				sublabel={ctx.scheduledMatchCount > 0
					? `${ctx.teamName} · ${ctx.scheduledMatchCount} upcoming`
					: ctx.teamName}
			/>
		{/if}
	{/await}
</div>

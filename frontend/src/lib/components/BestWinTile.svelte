<script lang="ts">
	import StatTile from '$lib/components/StatTile.svelte';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import { formatName } from '$lib/utils';

	interface Props {
		label: string;
		opponentId: string | null;
		opponentName: string;
		opponentClass: string | null;
	}

	let { label, opponentId, opponentName, opponentClass }: Props = $props();
</script>

<StatTile {label}>
	<p class="flex items-center gap-1.5 text-xl font-semibold">
		{#if opponentId}
			<a href="/players/{opponentId}" class="truncate hover:underline">
				{formatName(opponentName)}
			</a>
		{:else}
			<span class="truncate">{formatName(opponentName)}</span>
		{/if}
		<ClassBadge classification={opponentClass} />
	</p>
</StatTile>

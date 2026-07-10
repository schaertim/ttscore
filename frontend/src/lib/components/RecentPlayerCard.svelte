<script lang="ts">
	import { XIcon } from 'phosphor-svelte';
	import PlayerTile from './PlayerTile.svelte';
	import { removeRecentPlayer } from '$lib/recentPlayers';

	interface Props {
		id: string;
		fullName: string;
		classification?: string | null;
		currentClubName?: string | null;
		onremove?: () => void;
	}

	let { id, fullName, classification, currentClubName, onremove }: Props = $props();

	function handleRemove(e: MouseEvent) {
		e.preventDefault();
		e.stopPropagation();
		removeRecentPlayer(id);
		onremove?.();
	}
</script>

<PlayerTile {fullName} {classification} href={`/players/${id}`}>
		{#snippet content()}
			<p class="w-full truncate text-2xs tracking-wide text-muted-foreground">
				{currentClubName ?? '—'}
			</p>
		{/snippet}
		{#snippet corner()}
			<button
				onclick={handleRemove}
				class="pointer-events-auto rounded-full p-1 text-foreground/40 transition-colors hover:text-foreground"
				aria-label="Remove from recently viewed"
			>
				<XIcon size="16" />
			</button>
		{/snippet}
	</PlayerTile>

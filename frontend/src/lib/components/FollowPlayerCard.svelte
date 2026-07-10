<script lang="ts">
	import { enhance } from '$app/forms';
	import { StarIcon } from 'phosphor-svelte';
	import PlayerTile from './PlayerTile.svelte';

	interface Props {
		id: string;
		fullName: string;
		classification?: string | null;
		currentClubName?: string | null;
		followId: string;
		onunfollow?: () => void;
	}

	let { id, fullName, classification, currentClubName, followId, onunfollow }: Props = $props();
</script>

<PlayerTile {fullName} {classification} href={`/players/${id}`}>
		{#snippet content()}
			<p class="w-full truncate text-2xs tracking-wide text-muted-foreground">
				{currentClubName ?? '—'}
			</p>
		{/snippet}
		{#snippet corner()}
			<form
				method="POST"
				action="?/unfollow"
				use:enhance={() => {
					onunfollow?.();
					return async ({ update }) => {
						await update({ reset: false, invalidateAll: false });
					};
				}}
			>
				<input type="hidden" name="followId" value={followId} />
				<button
					type="submit"
					onclick={(e) => e.stopPropagation()}
					class="pointer-events-auto rounded-full p-1 text-foreground/60 transition-colors hover:text-foreground"
					aria-label="Unfollow"
				>
					<StarIcon weight="fill" size="16" />
				</button>
			</form>
		{/snippet}
	</PlayerTile>

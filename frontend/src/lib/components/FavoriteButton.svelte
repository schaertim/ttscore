<script lang="ts">
	import { enhance } from '$app/forms';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import type { ActionResult } from '@sveltejs/kit';
	import { StarIcon } from 'phosphor-svelte';

	interface Props {
		isFavorite: boolean;
		favoriteId: string | null;
		targetType: string;
		targetId: string;
		authenticated?: boolean;
	}

	let {
		isFavorite = $bindable(),
		favoriteId = $bindable(),
		targetType,
		targetId,
		authenticated = true
	}: Props = $props();

	function redirectToSignIn() {
		goto(`/signin?redirectTo=${encodeURIComponent(page.url.pathname)}`);
	}

	let loading = $state(false);

	function unfavoriteEnhance() {
		loading = true;
		return async ({ result, update }: { result: ActionResult; update(): Promise<void> }) => {
			loading = false;
			if (result.type === 'success') {
				isFavorite = false;
				favoriteId = null;
			} else {
				await update();
			}
		};
	}

	function favoriteEnhance() {
		loading = true;
		return async ({ result, update }: { result: ActionResult; update(): Promise<void> }) => {
			loading = false;
			if (result.type === 'success' && result.data) {
				isFavorite = true;
				favoriteId = result.data.favoriteId as unknown as string;
			} else {
				await update();
			}
		};
	}
</script>

{#if !authenticated}
	<button
		type="button"
		onclick={redirectToSignIn}
		title="Sign in to add to favorites"
		class="flex items-center justify-center rounded-full p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
	>
		<StarIcon size="20" />
	</button>
{:else if isFavorite}
	<form method="POST" action="?/unfavorite" use:enhance={unfavoriteEnhance}>
		<input type="hidden" name="favoriteId" value={favoriteId} />
		<button
			type="submit"
			disabled={loading}
			title="Remove from favorites"
			class="flex items-center justify-center rounded-full p-2 text-foreground transition-colors hover:bg-muted disabled:opacity-50"
		>
			<StarIcon size="20" weight="fill" />
		</button>
	</form>
{:else}
	<form method="POST" action="?/favorite" use:enhance={favoriteEnhance}>
		<input type="hidden" name="targetType" value={targetType} />
		<input type="hidden" name="targetId" value={targetId} />
		<button
			type="submit"
			disabled={loading}
			title="Add to favorites"
			class="flex items-center justify-center rounded-full p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground disabled:opacity-50"
		>
			<StarIcon size="20" />
		</button>
	</form>
{/if}

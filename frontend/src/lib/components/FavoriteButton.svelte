<script lang="ts">
	import { enhance } from '$app/forms';
	import { Star } from 'phosphor-svelte';

	interface Props {
		favorited: boolean;
		favoriteId: string | null;
		targetType: string;
		targetId: string;
	}

	let { favorited = $bindable(), favoriteId = $bindable(), targetType, targetId }: Props = $props();

	let loading = $state(false);
</script>

{#if favorited}
	<form
		method="POST"
		action="?/unfavorite"
		use:enhance={() => {
			loading = true;
			return async ({ result, update }) => {
				loading = false;
				if (result.type === 'success') {
					favorited = false;
					favoriteId = null;
				} else {
					await update();
				}
			};
		}}
	>
		<input type="hidden" name="favoriteId" value={favoriteId} />
		<button
			type="submit"
			disabled={loading}
			title="Remove from favorites"
			class="flex items-center justify-center rounded-full p-1.5 text-foreground transition-colors hover:bg-muted disabled:opacity-50"
		>
			<Star class="h-5 w-5" weight="fill" />
		</button>
	</form>
{:else}
	<form
		method="POST"
		action="?/favorite"
		use:enhance={() => {
			loading = true;
			return async ({ result, update }) => {
				loading = false;
				if (result.type === 'success' && result.data) {
					favorited = true;
					favoriteId = result.data.favoriteId as string;
				} else {
					await update();
				}
			};
		}}
	>
		<input type="hidden" name="targetType" value={targetType} />
		<input type="hidden" name="targetId" value={targetId} />
		<button
			type="submit"
			disabled={loading}
			title="Add to favorites"
			class="flex items-center justify-center rounded-full p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground disabled:opacity-50"
		>
			<Star class="h-5 w-5" />
		</button>
	</form>
{/if}

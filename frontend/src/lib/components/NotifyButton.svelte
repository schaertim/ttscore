<script lang="ts">
	import { enhance } from '$app/forms';
	import { Bell, BellRinging } from 'phosphor-svelte';

	interface Props {
		notifying: boolean;
		notifyId: string | null;
		targetType: string;
		targetId: string;
	}

	let { notifying = $bindable(), notifyId = $bindable(), targetType, targetId }: Props = $props();

	let loading = $state(false);
</script>

{#if notifying}
	<form
		method="POST"
		action="?/unfollow"
		use:enhance={() => {
			loading = true;
			return async ({ result, update }) => {
				loading = false;
				if (result.type === 'success') {
					notifying = false;
					notifyId = null;
				} else {
					await update();
				}
			};
		}}
	>
		<input type="hidden" name="notifyId" value={notifyId} />
		<button
			type="submit"
			disabled={loading}
			title="Turn off notifications"
			class="flex items-center justify-center rounded-full p-1.5 text-foreground transition-colors hover:bg-muted disabled:opacity-50"
		>
			<BellRinging class="h-5 w-5" weight="fill" />
		</button>
	</form>
{:else}
	<form
		method="POST"
		action="?/follow"
		use:enhance={() => {
			loading = true;
			return async ({ result, update }) => {
				loading = false;
				if (result.type === 'success' && result.data) {
					notifying = true;
					notifyId = result.data.notifyId as string;
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
			title="Turn on notifications"
			class="flex items-center justify-center rounded-full p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground disabled:opacity-50"
		>
			<Bell class="h-5 w-5" />
		</button>
	</form>
{/if}

<script lang="ts">
	import { enhance } from '$app/forms';
	import type { ActionResult } from '@sveltejs/kit';
	import { BellIcon, BellRingingIcon } from 'phosphor-svelte';

	interface Props {
		notifying: boolean;
		notifyId: string | null;
		targetType: string;
		targetId: string;
	}

	let { notifying = $bindable(), notifyId = $bindable(), targetType, targetId }: Props = $props();

	let loading = $state(false);

	function unfollowEnhance() {
		loading = true;
		return async ({ result, update }: { result: ActionResult; update(): Promise<void> }) => {
			loading = false;
			if (result.type === 'success') {
				notifying = false;
				notifyId = null;
			} else {
				await update();
			}
		};
	}

	function followEnhance() {
		loading = true;
		return async ({ result, update }: { result: ActionResult; update(): Promise<void> }) => {
			loading = false;
			if (result.type === 'success' && result.data) {
				notifying = true;
				notifyId = result.data.notifyId as unknown as string;
			} else {
				await update();
			}
		};
	}
</script>

{#if notifying}
	<form method="POST" action="?/unfollow" use:enhance={unfollowEnhance}>
		<input type="hidden" name="notifyId" value={notifyId} />
		<button
			type="submit"
			disabled={loading}
			title="Turn off notifications"
			class="flex items-center justify-center rounded-full p-1.5 text-foreground transition-colors hover:bg-muted disabled:opacity-50"
		>
			<BellRingingIcon size="20" weight="fill" />
		</button>
	</form>
{:else}
	<form method="POST" action="?/follow" use:enhance={followEnhance}>
		<input type="hidden" name="targetType" value={targetType} />
		<input type="hidden" name="targetId" value={targetId} />
		<button
			type="submit"
			disabled={loading}
			title="Turn on notifications"
			class="flex items-center justify-center rounded-full p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground disabled:opacity-50"
		>
			<BellIcon size="20" />
		</button>
	</form>
{/if}

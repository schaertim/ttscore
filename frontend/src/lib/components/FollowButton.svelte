<script lang="ts">
	import { enhance } from '$app/forms';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import type { ActionResult } from '@sveltejs/kit';
	import { StarIcon } from 'phosphor-svelte';

	interface Props {
		following: boolean;
		followId: string | null;
		/** Reset to false whenever the follow is created or removed. */
		notify: boolean;
		targetType: string;
		targetId: string;
		authenticated?: boolean;
	}

	let {
		following = $bindable(),
		followId = $bindable(),
		notify = $bindable(),
		targetType,
		targetId,
		authenticated = true
	}: Props = $props();

	function redirectToSignIn() {
		goto(`/signin?redirectTo=${encodeURIComponent(page.url.pathname)}`);
	}

	let loading = $state(false);

	function unfollowEnhance() {
		loading = true;
		return async ({ result, update }: { result: ActionResult; update(): Promise<void> }) => {
			loading = false;
			if (result.type === 'success') {
				following = false;
				followId = null;
				notify = false;
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
				following = true;
				followId = result.data.followId as unknown as string;
				notify = false;
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
		title="Sign in to follow"
		class="flex items-center justify-center rounded-full p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
	>
		<StarIcon size="20" />
	</button>
{:else if following}
	<form method="POST" action="?/unfollow" use:enhance={unfollowEnhance}>
		<input type="hidden" name="followId" value={followId} />
		<button
			type="submit"
			disabled={loading}
			title="Unfollow"
			class="flex items-center justify-center rounded-full p-2 text-foreground transition-colors hover:bg-muted disabled:opacity-50"
		>
			<StarIcon size="20" weight="fill" />
		</button>
	</form>
{:else}
	<form method="POST" action="?/follow" use:enhance={followEnhance}>
		<input type="hidden" name="targetType" value={targetType} />
		<input type="hidden" name="targetId" value={targetId} />
		<button
			type="submit"
			disabled={loading}
			title="Follow"
			class="flex items-center justify-center rounded-full p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground disabled:opacity-50"
		>
			<StarIcon size="20" />
		</button>
	</form>
{/if}

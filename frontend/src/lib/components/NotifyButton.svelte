<script lang="ts">
	import { enhance } from '$app/forms';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import type { ActionResult } from '@sveltejs/kit';
	import { BellIcon, BellRingingIcon } from 'phosphor-svelte';
	import { toast } from 'svelte-sonner';
	import { _ } from 'svelte-i18n';
	import { get } from 'svelte/store';

	interface Props {
		/** Notifications require an existing follow — the bell is disabled otherwise. */
		following: boolean;
		followId: string | null;
		notify: boolean;
		authenticated?: boolean;
	}

	let { following, followId, notify = $bindable(), authenticated = true }: Props = $props();

	function redirectToSignIn() {
		goto(`/signin?redirectTo=${encodeURIComponent(page.url.pathname)}`);
	}

	let loading = $state(false);

	function toggleEnhance() {
		loading = true;
		return async ({ result, update }: { result: ActionResult; update(): Promise<void> }) => {
			loading = false;
			if (result.type === 'success') {
				notify = !notify;
			} else {
				if (result.type === 'failure' && result.data?.reason === 'notify_pro') {
					toast.error(get(_)('pro.notify_title'), {
						description: get(_)('pro.notify_desc'),
						action: { label: get(_)('pro.unlock'), onClick: () => goto('/pro') }
					});
				}
				await update();
			}
		};
	}
</script>

{#if !authenticated}
	<button
		type="button"
		onclick={redirectToSignIn}
		title="Sign in to turn on notifications"
		class="flex items-center justify-center rounded-full p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
	>
		<BellIcon size="20" />
	</button>
{:else if !following}
	<button
		type="button"
		disabled
		title="Follow to enable notifications"
		class="flex items-center justify-center rounded-full p-2 text-muted-foreground opacity-40"
	>
		<BellIcon size="20" />
	</button>
{:else}
	<form method="POST" action="?/setNotify" use:enhance={toggleEnhance}>
		<input type="hidden" name="followId" value={followId} />
		<input type="hidden" name="notify" value={String(!notify)} />
		<button
			type="submit"
			disabled={loading}
			title={notify ? 'Turn off notifications' : 'Turn on notifications'}
			class="flex items-center justify-center rounded-full p-2 text-foreground transition-colors hover:bg-muted disabled:opacity-50"
		>
			{#if notify}
				<BellRingingIcon size="20" weight="fill" />
			{:else}
				<BellIcon size="20" />
			{/if}
		</button>
	</form>
{/if}

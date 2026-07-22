<script lang="ts">
	import { enhance } from '$app/forms';
	import { goto } from '$app/navigation';
	import type { ActionResult } from '@sveltejs/kit';
	import { BellIcon, BellRingingIcon } from 'phosphor-svelte';
	import { toast } from 'svelte-sonner';
	import { _ } from 'svelte-i18n';
	import { get } from 'svelte/store';
	import IconButton from '$lib/components/IconButton.svelte';

	interface Props {
		/** Notifications require an existing follow — the bell is hidden otherwise. */
		following: boolean;
		followId: string | null;
		notify: boolean;
		authenticated?: boolean;
	}

	let { following, followId, notify = $bindable(), authenticated = true }: Props = $props();

	function redirectToSignIn() {
		goto('/signin');
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
	<IconButton onclick={redirectToSignIn} title={$_('common.sign_in_to_notify')}>
		<BellIcon size={20} />
	</IconButton>
{:else if following}
	<form method="POST" action="?/setNotify" use:enhance={toggleEnhance}>
		<input type="hidden" name="followId" value={followId} />
		<input type="hidden" name="notify" value={String(!notify)} />
		<IconButton
			type="submit"
			tone="foreground"
			disabled={loading}
			title={$_(notify ? 'common.notifications_off' : 'common.notifications_on')}
		>
			{#if notify}
				<BellRingingIcon size={20} weight="fill" />
			{:else}
				<BellIcon size={20} />
			{/if}
		</IconButton>
	</form>
{/if}

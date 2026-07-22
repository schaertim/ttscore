<script lang="ts">
	import { enhance } from '$app/forms';
	import { goto } from '$app/navigation';
	import type { ActionResult } from '@sveltejs/kit';
	import { StarIcon } from 'phosphor-svelte';
	import { toast } from 'svelte-sonner';
	import { _ } from 'svelte-i18n';
	import { get } from 'svelte/store';
	import IconButton from '$lib/components/IconButton.svelte';

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
		// eslint-disable-next-line no-useless-assignment -- write-only here; the parent reads it via the binding
		notify = $bindable(),
		targetType,
		targetId,
		authenticated = true
	}: Props = $props();

	function redirectToSignIn() {
		goto('/signin');
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
				if (result.type === 'failure' && result.data?.reason === 'follow_limit') {
					toast.error(get(_)('pro.follow_limit_title'), {
						description: get(_)('pro.follow_limit_desc'),
						action: { label: get(_)('pro.unlock'), onClick: () => goto('/pro') }
					});
				}
				await update();
			}
		};
	}
</script>

{#if !authenticated}
	<IconButton onclick={redirectToSignIn} title={$_('common.sign_in_to_follow')}>
		<StarIcon size={20} />
	</IconButton>
{:else if following}
	<form method="POST" action="?/unfollow" use:enhance={unfollowEnhance}>
		<input type="hidden" name="followId" value={followId} />
		<IconButton type="submit" tone="foreground" disabled={loading} title={$_('common.unfollow')}>
			<StarIcon size={20} weight="fill" />
		</IconButton>
	</form>
{:else}
	<form method="POST" action="?/follow" use:enhance={followEnhance}>
		<input type="hidden" name="targetType" value={targetType} />
		<input type="hidden" name="targetId" value={targetId} />
		<IconButton type="submit" disabled={loading} title={$_('common.follow')}>
			<StarIcon size={20} />
		</IconButton>
	</form>
{/if}

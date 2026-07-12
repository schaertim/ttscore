<script lang="ts">
	import type { SupabaseClient } from '@supabase/supabase-js';
	import { BellRingingIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';
	import { getSubscription, subscribe, unsubscribe } from '$lib/push';
	import InfoItem from '$lib/components/InfoItem.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';

	interface Props {
		supabase: SupabaseClient;
	}

	let { supabase }: Props = $props();

	let pushSubscribed = $state<boolean | null>(null);
	let pushLoading = $state(false);
	let pushUnsupported = $state(false);

	$effect(() => {
		if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
			pushUnsupported = true;
			return;
		}
		getSubscription().then((sub) => {
			pushSubscribed = sub !== null;
		});
	});

	async function togglePush() {
		pushLoading = true;
		try {
			const { data: sessionData } = await supabase.auth.getSession();
			const token = sessionData.session?.access_token ?? '';
			if (pushSubscribed) {
				await unsubscribe(token);
				pushSubscribed = false;
			} else {
				const ok = await subscribe(token);
				if (ok) pushSubscribed = true;
			}
		} finally {
			pushLoading = false;
		}
	}
</script>

{#if !pushUnsupported}
	<section class="space-y-3">
		<SectionLabel label={$_('account.push_notifications')} icon={BellRingingIcon} />
		<InfoItem
			variant="muted"
			title={$_(pushSubscribed ? 'account.push_enabled' : 'account.push_disabled')}
			description={pushSubscribed
				? $_('account.push_enabled_desc')
				: $_('account.push_disabled_desc')}
			onclick={togglePush}
			disabled={pushLoading || pushSubscribed === null}
		>
			{#snippet trailing()}
				<BellRingingIcon
					size={20}
					weight={pushSubscribed ? 'fill' : 'regular'}
					class="m-1 shrink-0 text-muted-foreground"
				/>
			{/snippet}
		</InfoItem>
	</section>
{/if}

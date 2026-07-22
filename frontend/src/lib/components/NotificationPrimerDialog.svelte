<script lang="ts">
	import { BellRingingIcon } from 'phosphor-svelte';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { _ } from 'svelte-i18n';
	import { notificationPrimer, resolveNotificationPrimer } from '$lib/notificationPrimer.svelte';

	function handleOpenChange(next: boolean) {
		// Backdrop click or ESC — treat as "not now" so the awaiting caller unblocks.
		if (!next) resolveNotificationPrimer(false);
	}
</script>

<Dialog.Root bind:open={notificationPrimer.open} onOpenChange={handleOpenChange}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header class="items-center text-center">
			<div class="rounded-2xl bg-primary/10 p-4">
				<BellRingingIcon size={32} class="text-primary" weight="fill" />
			</div>
			<Dialog.Title class="text-xl font-black tracking-tight">
				{$_('push_primer.headline')}
			</Dialog.Title>
			<Dialog.Description class="text-sm leading-relaxed text-muted-foreground">
				{$_('push_primer.body')}
			</Dialog.Description>
		</Dialog.Header>

		<Dialog.Footer class="flex-col gap-2 sm:flex-col sm:space-x-0">
			<Button onclick={() => resolveNotificationPrimer(true)} class="w-full">
				{$_('push_primer.enable')}
			</Button>
			<Button variant="ghost" class="w-full" onclick={() => resolveNotificationPrimer(false)}>
				{$_('push_primer.not_now')}
			</Button>
		</Dialog.Footer>
	</Dialog.Content>
</Dialog.Root>

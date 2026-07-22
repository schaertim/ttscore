<script lang="ts">
	import { onDestroy } from 'svelte';
	import { DeviceMobileIcon, DownloadSimpleIcon, ExportIcon, PlusSquareIcon } from 'phosphor-svelte';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { installTarget, acceptInstall, markInstallPrimerSeen } from '$lib/installPrimer.svelte';
	import { _ } from 'svelte-i18n';

	interface Props {
		/** Only signed-in users are prompted — push (the reason to install on iOS) needs an account. */
		loggedIn: boolean;
	}
	let { loggedIn }: Props = $props();

	// A short beat after login rather than immediately, so it doesn't collide with the landing render.
	const DELAY_MS = 5000;

	let open = $state(false);
	let platform = $state<'ios' | 'android' | null>(null);
	let scheduled = false;
	let timer: ReturnType<typeof setTimeout> | undefined;

	// Fires once, when `loggedIn` first becomes true (covers both a returning signed-in user on load
	// and the redirect right after sign-in, since the root layout doesn't remount). installTarget()
	// already returns null on desktop, when already installed, or when dismissed before — so this
	// only ever schedules on iOS/Android that still needs installing.
	$effect(() => {
		if (!loggedIn || scheduled) return;
		const target = installTarget();
		if (!target) return;
		scheduled = true;
		timer = setTimeout(() => {
			platform = target;
			open = true;
		}, DELAY_MS);
	});

	onDestroy(() => clearTimeout(timer));

	function handleOpenChange(next: boolean) {
		// Mark seen on any close (button, backdrop, ESC) so it never reappears.
		if (!next) markInstallPrimerSeen();
	}

	async function installOnAndroid() {
		await acceptInstall();
		open = false;
	}
</script>

<Dialog.Root bind:open onOpenChange={handleOpenChange}>
	<Dialog.Content class="sm:max-w-md">
		{#if platform === 'ios'}
			<Dialog.Header class="items-center text-center">
				<div class="rounded-2xl bg-primary/10 p-4">
					<DeviceMobileIcon size={32} class="text-primary" weight="fill" />
				</div>
				<Dialog.Title class="text-xl font-black tracking-tight">
					{$_('install_primer.ios_headline')}
				</Dialog.Title>
				<Dialog.Description class="text-sm leading-relaxed text-muted-foreground">
					{$_('install_primer.ios_body')}
				</Dialog.Description>
			</Dialog.Header>

			<div class="flex flex-col gap-3 py-2">
				<div class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3">
					<span
						class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary"
					>
						1
					</span>
					<ExportIcon size={20} class="shrink-0 text-muted-foreground" />
					<span class="text-sm">{$_('install_primer.ios_step1')}</span>
				</div>
				<div class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3">
					<span
						class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary"
					>
						2
					</span>
					<PlusSquareIcon size={20} class="shrink-0 text-muted-foreground" />
					<span class="text-sm">{$_('install_primer.ios_step2')}</span>
				</div>
			</div>

			<Dialog.Footer class="flex-col gap-2 sm:flex-col sm:space-x-0">
				<Button onclick={() => (open = false)} class="w-full">
					{$_('install_primer.got_it')}
				</Button>
			</Dialog.Footer>
		{:else}
			<Dialog.Header class="items-center text-center">
				<div class="rounded-2xl bg-primary/10 p-4">
					<DownloadSimpleIcon size={32} class="text-primary" weight="fill" />
				</div>
				<Dialog.Title class="text-xl font-black tracking-tight">
					{$_('install_primer.android_headline')}
				</Dialog.Title>
				<Dialog.Description class="text-sm leading-relaxed text-muted-foreground">
					{$_('install_primer.android_body')}
				</Dialog.Description>
			</Dialog.Header>

			<Dialog.Footer class="flex-col gap-2 sm:flex-col sm:space-x-0">
				<Button onclick={installOnAndroid} class="w-full">{$_('install_primer.install')}</Button>
				<Button variant="ghost" class="w-full" onclick={() => (open = false)}>
					{$_('install_primer.not_now')}
				</Button>
			</Dialog.Footer>
		{/if}
	</Dialog.Content>
</Dialog.Root>

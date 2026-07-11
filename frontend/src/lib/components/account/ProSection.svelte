<script lang="ts">
	import { SparkleIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';
	import { Button } from '$lib/components/ui/button/index.js';
	import InfoItem from '$lib/components/InfoItem.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';

	interface Props {
		isPro: boolean;
		/** Localised renewal date, or null when unknown. */
		renewDate: string | null;
	}

	let { isPro, renewDate }: Props = $props();
</script>

<section class="space-y-3">
	<SectionLabel label={$_('account.pro_section')} icon={SparkleIcon} />
	{#if isPro}
		<div class="space-y-3 rounded-xl border border-primary/20 bg-primary/5 px-4 py-3">
			<div class="flex items-center justify-between gap-3">
				<div class="min-w-0">
					<p class="flex items-center gap-1.5 text-sm font-semibold">
						<SparkleIcon size={16} weight="fill" class="text-primary" />
						{$_('account.pro_active')}
					</p>
					{#if renewDate}
						<p class="text-xs text-muted-foreground">
							{$_('account.pro_renews', { values: { date: renewDate } })}
						</p>
					{/if}
				</div>
			</div>
			<form method="POST" action="?/billingPortal">
				<Button type="submit" variant="outline" size="sm" class="w-full rounded-full font-semibold">
					{$_('account.manage_billing')}
				</Button>
			</form>
		</div>
	{:else}
		<InfoItem
			href="/pro"
			icon={SparkleIcon}
			title={$_('account.pro_upgrade')}
			description={$_('account.pro_upgrade_desc')}
		/>
	{/if}
</section>

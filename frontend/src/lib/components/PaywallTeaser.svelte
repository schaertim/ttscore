<script lang="ts">
	import { LockKeyIcon, SparkleIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';
	import { analytics, type ProSource } from '$lib/analytics';

	interface Props {
		title: string;
		description: string;
		source: ProSource;
	}

	let { title, description, source }: Props = $props();
</script>

<div
	class="flex flex-col items-center gap-3 rounded-2xl border border-primary/20 bg-primary/5 p-8 text-center"
>
	<div class="rounded-full bg-primary/10 p-3">
		<LockKeyIcon size="24" class="text-primary" weight="fill" />
	</div>
	<div class="space-y-1">
		<p class="text-base font-semibold">{title}</p>
		<p class="mx-auto max-w-xs text-sm text-muted-foreground">{description}</p>
	</div>
	<a
		href="/pro"
		onclick={() => analytics.proPrompted(source)}
		class="mt-1 inline-flex items-center gap-1.5 rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
	>
		<SparkleIcon size="16" weight="fill" />
		{$_('pro.unlock')}
	</a>
</div>

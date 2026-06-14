<script lang="ts">
	import * as Card from '$lib/components/ui/card/index.js';
	import { UserCirclePlusIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';
</script>

<Card.Root class="border-primary/20 bg-primary/5">
	<Card.Content class="flex items-start gap-4 p-4">
		<div class="mt-1 shrink-0 rounded-full bg-primary/10 p-2">
			<UserCirclePlusIcon size="20" class="text-primary" />
		</div>
		<div class="min-w-0 flex-1">
			<p class="font-semibold">{$_('set_player.banner_title')}</p>
			<p class="mt-1 text-sm text-muted-foreground">
				{$_('set_player.banner_desc')}
			</p>
			<a href="/account" class="mt-3 inline-block text-sm font-semibold text-primary hover:underline">
				{$_('set_player.banner_cta')}
			</a>
		</div>
	</Card.Content>
</Card.Root>

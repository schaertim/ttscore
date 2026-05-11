<script lang="ts">
	import * as Card from '$lib/components/ui/card/index.js';
	import { UserCirclePlus } from 'phosphor-svelte';
</script>

<Card.Root class="border-primary/20 bg-primary/5">
	<Card.Content class="flex items-start gap-4 p-5">
		<div class="mt-0.5 shrink-0 rounded-full bg-primary/10 p-2">
			<UserCirclePlus class="h-5 w-5 text-primary" />
		</div>
		<div class="min-w-0 flex-1">
			<p class="font-bold">Make it yours</p>
			<p class="mt-0.5 text-sm text-muted-foreground">
				Set your player to see your ELO, next match, and recent results right here.
			</p>
			<a href="/account" class="mt-3 inline-block text-sm font-bold text-primary hover:underline">
				Set your player →
			</a>
		</div>
	</Card.Content>
</Card.Root>

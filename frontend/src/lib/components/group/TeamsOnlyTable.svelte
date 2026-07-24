<script lang="ts">
	import type { TeamBasic } from '$lib/api';
	import * as Table from '$lib/components/ui/table/index.js';

	interface Props {
		teams: TeamBasic[];
	}

	let { teams }: Props = $props();
</script>

<div class="overflow-hidden rounded-xl border border-border">
	<Table.Root>
		<Table.Body>
			{#each teams as team (team.id)}
				<Table.Row class="border-border">
					<Table.Cell class="text-sm font-semibold">
						<a href="/teams/{team.id}" class="hover:underline">
							{team.name}
						</a>
					</Table.Cell>
				</Table.Row>
			{/each}
		</Table.Body>
	</Table.Root>
</div>

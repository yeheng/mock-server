<script setup>
import { computed } from 'vue'
import { useStubsStore } from '@/stores/stubs'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'

const stubs = useStubsStore()

function onToggle(id) {
  stubs.toggle(id)
}

function onRemove(id) {
  stubs.remove(id)
}
</script>

<template>
  <Card>
    <CardHeader>
      <CardTitle>Stub Mappings</CardTitle>
      <CardDescription>List of all stub mappings.</CardDescription>
    </CardHeader>
    <CardContent>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Method</TableHead>
            <TableHead>URL</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Response Status</TableHead>
            <TableHead>Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <template v-if="stubs.items.length > 0">
            <TableRow v-for="item in stubs.items" :key="item.id">
              <TableCell>
                <Badge variant="outline">{{ item.request.method }}</Badge>
              </TableCell>
              <TableCell>{{ item.request.url }}</TableCell>
              <TableCell>
                <Badge :variant="item.enabled ? 'default' : 'destructive'">{{ item.enabled ? 'Enabled' : 'Disabled' }}</Badge>
              </TableCell>
              <TableCell>{{ item.response.status }}</TableCell>
              <TableCell class="space-x-2">
                <Button variant="outline" size="sm" @click="onToggle(item.id)">{{ item.enabled ? 'Disable' : 'Enable' }}</Button>
                <Button variant="destructive" size="sm" @click="onRemove(item.id)">Delete</Button>
              </TableCell>
            </TableRow>
          </template>
          <TableRow v-else>
            <TableCell colspan="5" class="text-center">No stubs found.</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </CardContent>
  </Card>
</template>
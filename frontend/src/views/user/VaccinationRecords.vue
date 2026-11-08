<template>
  <el-card shadow="never" header="接种记录">
    <el-table :data="records" v-loading="loading">
      <el-table-column prop="vaccineName" label="疫苗名称" width="140" />
      <el-table-column prop="batchNo" label="批次编号" width="160" />
      <el-table-column prop="institutionName" label="接种机构" width="160" />
      <el-table-column prop="nurseName" label="接种护士" width="100" />
      <el-table-column label="接种时间" width="180">
        <template #default="{ row }">{{ formatTime(row.vaccinationTime) }}</template>
      </el-table-column>
      <el-table-column label="剂次" width="90">
        <template #default="{ row }">
          <el-tag size="small" type="success">第 {{ row.doseNumber }} 剂</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import http from '../../api/http'

const records = ref([])
const loading = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await http.get('/api/user/vaccination-records')
    records.value = res.data
  } finally {
    loading.value = false
  }
}

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}
</script>

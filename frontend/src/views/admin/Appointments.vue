<template>
  <el-card shadow="never" header="待接种预约处理">
    <el-table :data="appointments" v-loading="loading">
      <el-table-column prop="userName" label="居民姓名" width="110" />
      <el-table-column prop="vaccineName" label="疫苗" width="140" />
      <el-table-column prop="batchNo" label="锁定批次" width="170" />
      <el-table-column label="班次" width="170">
        <template #default="{ row }">{{ row.shiftDate }} {{ row.startTime }}-{{ row.endTime }}</template>
      </el-table-column>
      <el-table-column prop="nurseName" label="护士" width="100" />
      <el-table-column label="剂次" width="80">
        <template #default="{ row }">第 {{ row.doseNumber }} 剂</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="success" :loading="confirmingId === row.appointmentId" @click="onConfirm(row)">
            确认接种
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'

const appointments = ref([])
const loading = ref(false)
const confirmingId = ref(null)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await http.get('/api/institution-admin/appointments')
    appointments.value = res.data
  } finally {
    loading.value = false
  }
}

async function onConfirm(row) {
  try {
    await ElMessageBox.confirm(
      `确认为居民 ${row.userName} 执行 ${row.vaccineName}（批次 ${row.batchNo}）接种吗？`,
      '确认接种',
      { type: 'warning' },
    )
  } catch {
    return
  }
  confirmingId.value = row.appointmentId
  try {
    await http.post(`/api/institution-admin/appointments/${row.appointmentId}/confirm`)
    ElMessage.success('接种完成')
    await load()
  } catch {
    /* 错误提示由拦截器统一处理 */
  } finally {
    confirmingId.value = null
  }
}
</script>

<template>
  <el-card shadow="never" header="我的预约">
    <el-table :data="appointments" v-loading="loading">
      <el-table-column prop="vaccineName" label="疫苗" width="140" />
      <el-table-column prop="institutionName" label="接种机构" width="160" />
      <el-table-column label="班次" width="170">
        <template #default="{ row }">{{ row.shiftDate }} {{ row.startTime }}-{{ row.endTime }}</template>
      </el-table-column>
      <el-table-column prop="nurseName" label="护士" width="90" />
      <el-table-column label="剂次" width="80">
        <template #default="{ row }">第 {{ row.doseNumber }} 剂</template>
      </el-table-column>
      <el-table-column label="预约日期" width="110">
        <template #default="{ row }">{{ row.appointmentDate }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag[row.status]">{{ statusText[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="cancelReason" label="取消原因" min-width="130" show-overflow-tooltip />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" size="small" type="danger" plain @click="onCancel(row)">
            取消预约
          </el-button>
          <span v-else>-</span>
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

const statusText = { 1: '待接种', 2: '已完成', 3: '已取消' }
const statusTag = { 1: 'primary', 2: 'success', 3: 'info' }

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await http.get('/api/user/appointments')
    appointments.value = res.data
  } finally {
    loading.value = false
  }
}

async function onCancel(row) {
  try {
    await ElMessageBox.confirm(
      `确定取消该预约吗？（班次开始前 24 小时内不可取消）`,
      '取消预约',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await http.delete(`/api/user/appointments/${row.appointmentId}`)
    ElMessage.success('预约已取消')
    await load()
  } catch {
    /* 错误提示由拦截器统一处理 */
  }
}
</script>

<template>
  <div v-loading="loading">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <span class="title">{{ alerts.institutionName }} · 库存预警看板（{{ alerts.scanDate }}）</span>
        <el-button type="primary" :loading="scanning" @click="onScan">立即扫描</el-button>
      </div>
    </el-card>

    <!-- 临期（标黄） -->
    <el-card shadow="never" class="block-card">
      <template #header>
        <span class="block-title"><el-tag type="warning" effect="dark">临期</el-tag> 距有效期 ≤ 30 天（仍可出库）</span>
      </template>
      <el-table :data="alerts.nearExpiryStocks" size="small">
        <el-table-column prop="batchNo" label="批次号" width="170" />
        <el-table-column prop="vaccineName" label="疫苗" width="150" />
        <el-table-column prop="manufacturerName" label="生产商" width="170" />
        <el-table-column prop="quantity" label="库存" width="70" />
        <el-table-column prop="lockedQuantity" label="锁定" width="70" />
        <el-table-column prop="expiryDate" label="有效期至" width="110" />
        <el-table-column label="剩余天数" width="90">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ row.daysToExpiry }} 天</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="onTrace(row)">追溯</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 过期（红色封锁） -->
    <el-card shadow="never" class="block-card">
      <template #header>
        <span class="block-title"><el-tag type="danger" effect="dark">过期封锁</el-tag> 已过有效期，禁止出库</span>
      </template>
      <el-table :data="alerts.expiredStocks" size="small">
        <el-table-column prop="batchNo" label="批次号" width="170" />
        <el-table-column prop="vaccineName" label="疫苗" width="150" />
        <el-table-column prop="manufacturerName" label="生产商" width="170" />
        <el-table-column prop="quantity" label="库存" width="70" />
        <el-table-column prop="lockedQuantity" label="锁定" width="70" />
        <el-table-column prop="expiryDate" label="有效期至" width="110" />
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="onTrace(row)">追溯</el-button>
            <el-button size="small" type="danger" plain @click="onDestroy(row)">销毁</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 低库存 -->
    <el-card shadow="never" class="block-card">
      <template #header>
        <span class="block-title"><el-tag type="info" effect="dark">低库存</el-tag> 可出库合计 ≤ {{ lowThreshold }} 支，请及时补货</span>
      </template>
      <el-table :data="alerts.lowStockVaccines" size="small">
        <el-table-column prop="vaccineName" label="疫苗" width="200" />
        <el-table-column prop="totalQuantity" label="可出库合计" width="120" />
        <el-table-column prop="threshold" label="预警阈值" width="120" />
      </el-table>
    </el-card>

    <!-- 追溯对话框 -->
    <el-dialog v-model="traceVisible" title="批次追溯链" width="560px">
      <el-descriptions v-if="trace" :column="1" border size="small">
        <el-descriptions-item label="库存记录 ID">{{ trace.stockId }}</el-descriptions-item>
        <el-descriptions-item label="疫苗">{{ trace.vaccineName }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ trace.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="生产商">{{ trace.manufacturerName }}</el-descriptions-item>
        <el-descriptions-item label="生产日期">{{ trace.productionDate }}（产量 {{ trace.productionQuantity }} 支）</el-descriptions-item>
        <el-descriptions-item label="有效期至">{{ trace.expiryDate }}</el-descriptions-item>
        <el-descriptions-item label="供货信息">{{ trace.supplyInstitutionName }} · {{ trace.supplyDate }} · 供 {{ trace.supplyQuantity }} 支</el-descriptions-item>
        <el-descriptions-item label="当前库存">{{ trace.quantity }} 支（锁定 {{ trace.lockedQuantity }}）</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'

const alerts = ref({ nearExpiryStocks: [], expiredStocks: [], lowStockVaccines: [] })
const loading = ref(false)
const scanning = ref(false)
const traceVisible = ref(false)
const trace = ref(null)
const lowThreshold = ref(10)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await http.get('/api/institution-admin/alerts')
    alerts.value = res.data
    if (res.data.lowStockVaccines?.[0]?.threshold) {
      lowThreshold.value = res.data.lowStockVaccines[0].threshold
    }
  } finally {
    loading.value = false
  }
}

async function onScan() {
  scanning.value = true
  try {
    const res = await http.post('/api/institution-admin/alerts/scan')
    ElMessage.success(
      `扫描完成：临期标记 ${res.data.nearExpiryMarked} 批，过期封锁 ${res.data.expiredBlocked} 批，自动取消预约 ${res.data.cancelledAppointments} 条`,
    )
    await load()
  } finally {
    scanning.value = false
  }
}

async function onTrace(row) {
  const res = await http.get(`/api/institution-admin/stocks/${row.stockId}/trace`)
  trace.value = res.data
  traceVisible.value = true
}

async function onDestroy(row) {
  try {
    await ElMessageBox.confirm(
      `确认销毁过期批次 ${row.batchNo} 吗？销毁后库存清零、记录保留（可追溯）。`,
      '销毁疫苗',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await http.post(`/api/institution-admin/stocks/${row.stockId}/destroy`)
    ElMessage.success('销毁完成')
    await load()
  } catch {
    /* 错误提示由拦截器统一处理 */
  }
}
</script>

<style scoped>
.toolbar-card {
  margin-bottom: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  font-weight: 600;
}

.block-card {
  margin-bottom: 12px;
}

.block-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
</style>

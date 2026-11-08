<template>
  <el-row :gutter="12" v-loading="loadingVaccines">
    <!-- 疫苗列表 -->
    <el-col :span="7">
      <el-card shadow="never" header="① 选择疫苗">
        <el-table :data="vaccines" highlight-current-row @current-change="onVaccineChange" max-height="620">
          <el-table-column prop="name" label="疫苗名称" />
          <el-table-column prop="type" label="类型" width="90" />
          <el-table-column label="剂次" width="70">
            <template #default="{ row }">{{ row.dosesRequired }} 剂</template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-col>
    <!-- 机构列表 -->
    <el-col :span="8">
      <el-card shadow="never" header="② 选择接种机构">
        <el-empty v-if="!selectedVaccine" description="请先选择疫苗" :image-size="80" />
        <el-table v-else :data="institutions" highlight-current-row @current-change="onInstitutionChange" max-height="620" v-loading="loadingInst">
          <el-table-column prop="name" label="机构名称" />
          <el-table-column prop="region" label="区域" width="90" />
          <el-table-column label="可约余量" width="90">
            <template #default="{ row }">
              <el-tag size="small" type="success">{{ row.totalQuantity }} 支</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-col>
    <!-- 批次 + 班次 + 预约 -->
    <el-col :span="9">
      <el-card shadow="never" header="③ 批次余量与班次预约">
        <el-empty v-if="!selectedInstitution" description="请选择机构" :image-size="80" />
        <template v-else>
          <div class="section-title">批次余量（FEFO 出库顺序）</div>
          <el-table :data="batchStocks" size="small" max-height="150" v-loading="loadingBatch">
            <el-table-column prop="batchNo" label="批次号" />
            <el-table-column prop="expiryDate" label="有效期至" width="105" />
            <el-table-column prop="quantity" label="余量" width="70" />
          </el-table>
          <div class="section-title">可约班次</div>
          <el-table :data="shifts" size="small" highlight-current-row @current-change="onShiftChange" max-height="230" v-loading="loadingShift">
            <el-table-column prop="shiftDate" label="日期" width="105" />
            <el-table-column label="时段" width="130">
              <template #default="{ row }">{{ row.startTime }} - {{ row.endTime }}</template>
            </el-table-column>
            <el-table-column prop="nurseName" label="护士" width="80" />
            <el-table-column label="余位" width="80">
              <template #default="{ row }">{{ row.maxAppointments - row.currentCount }}</template>
            </el-table-column>
          </el-table>
          <div class="book-row">
            <el-button type="primary" :disabled="!selectedShift" :loading="booking" @click="onBook">
              预约该班次
            </el-button>
            <span v-if="selectedShift" class="hint">
              已选：{{ selectedShift.shiftDate }} {{ selectedShift.startTime }}-{{ selectedShift.endTime }}
            </span>
          </div>
        </template>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'

const vaccines = ref([])
const institutions = ref([])
const batchStocks = ref([])
const shifts = ref([])
const selectedVaccine = ref(null)
const selectedInstitution = ref(null)
const selectedShift = ref(null)
const loadingVaccines = ref(false)
const loadingInst = ref(false)
const loadingBatch = ref(false)
const loadingShift = ref(false)
const booking = ref(false)

onMounted(loadVaccines)

async function loadVaccines() {
  loadingVaccines.value = true
  try {
    const res = await http.get('/api/user/vaccines')
    vaccines.value = res.data
  } finally {
    loadingVaccines.value = false
  }
}

async function onVaccineChange(row) {
  selectedVaccine.value = row
  selectedInstitution.value = null
  selectedShift.value = null
  batchStocks.value = []
  shifts.value = []
  if (!row) return
  loadingInst.value = true
  try {
    const res = await http.get('/api/user/institutions')
    // 组合隐藏：仅展示该疫苗有可出库库存的机构
    institutions.value = res.data
      .filter((i) => i.availableVaccines?.some((v) => v.vaccineId === row.vaccineId))
      .map((i) => ({
        ...i,
        totalQuantity: i.availableVaccines.find((v) => v.vaccineId === row.vaccineId)?.totalQuantity || 0,
      }))
  } finally {
    loadingInst.value = false
  }
}

async function onInstitutionChange(row) {
  selectedInstitution.value = row
  selectedShift.value = null
  batchStocks.value = []
  shifts.value = []
  if (!row) return
  loadingBatch.value = true
  loadingShift.value = true
  try {
    const [batchRes, shiftRes] = await Promise.all([
      http.get(`/api/user/institutions/${row.institutionId}/vaccines/${selectedVaccine.value.vaccineId}/batches`),
      http.get('/api/user/shifts', { params: { institutionId: row.institutionId, vaccineId: selectedVaccine.value.vaccineId } }),
    ])
    batchStocks.value = batchRes.data
    shifts.value = shiftRes.data
  } finally {
    loadingBatch.value = false
    loadingShift.value = false
  }
}

function onShiftChange(row) {
  selectedShift.value = row
}

async function onBook() {
  if (!selectedShift.value) return
  booking.value = true
  try {
    const res = await http.post('/api/user/appointments', {
      vaccineId: selectedVaccine.value.vaccineId,
      shiftId: selectedShift.value.shiftId,
    })
    ElMessage.success(`预约成功，锁定批次：${res.data.batchNo}（第 ${res.data.doseNumber} 剂）`)
    // 刷新班次与余量
    await onInstitutionChange(selectedInstitution.value)
    selectedShift.value = null
  } finally {
    booking.value = false
  }
}
</script>

<style scoped>
.section-title {
  font-weight: 600;
  margin: 8px 0 6px;
  font-size: 14px;
  color: #303133;
}

.book-row {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.hint {
  color: #909399;
  font-size: 13px;
}
</style>

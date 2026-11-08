<template>
  <div v-loading="loading">
    <!-- 库存预警摘要（辅助排班规避"有位无苗"） -->
    <el-card shadow="never" class="block-card">
      <template #header>本机构疫苗库存（辅助排班）</template>
      <el-table :data="overview.vaccineSummaries" size="small">
        <el-table-column prop="vaccineName" label="疫苗" width="160" />
        <el-table-column prop="totalQuantity" label="可用库存" width="100" />
        <el-table-column prop="totalLocked" label="已锁定" width="100" />
        <el-table-column label="预警标志" min-width="260">
          <template #default="{ row }">
            <el-tag v-if="row.nearExpiry" size="small" type="warning">临期</el-tag>
            <el-tag v-if="row.lowStock" size="small" type="info">低库存</el-tag>
            <el-tag v-if="row.expiredBlocked" size="small" type="danger">有过期封锁批次</el-tag>
            <span v-if="!row.nearExpiry && !row.lowStock && !row.expiredBlocked" class="normal">正常</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 班次管理 -->
    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <span>班次管理</span>
          <el-button type="primary" @click="openCreate">新增班次</el-button>
        </div>
      </template>
      <el-table :data="overview.shifts">
        <el-table-column prop="shiftDate" label="日期" width="115" />
        <el-table-column label="时段" width="140">
          <template #default="{ row }">{{ row.startTime }} - {{ row.endTime }}</template>
        </el-table-column>
        <el-table-column prop="nurseName" label="护士" width="100" />
        <el-table-column label="名额" width="120">
          <template #default="{ row }">{{ row.currentCount }} / {{ row.maxAppointments }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '开诊' : '停诊' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="160">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.shiftId ? '编辑班次' : '新增班次'" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="护士" required>
          <el-select v-model="form.nurseId" style="width: 100%">
            <el-option v-for="n in nurses" :key="n.nurseId" :label="`${n.name}（${n.title || '护士'}）`" :value="n.nurseId" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期" required>
          <el-date-picker v-model="form.shiftDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-time-picker v-model="form.startTime" format="HH:mm" value-format="HH:mm" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-time-picker v-model="form.endTime" format="HH:mm" value-format="HH:mm" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最大人数" required>
          <el-input-number v-model="form.maxAppointments" :min="1" :max="500" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">开诊</el-radio>
            <el-radio :value="3">停诊</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const overview = ref({ vaccineSummaries: [], shifts: [] })
const nurses = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({
  shiftId: null,
  nurseId: null,
  shiftDate: '',
  startTime: '',
  endTime: '',
  maxAppointments: 20,
  status: 1,
})

onMounted(async () => {
  await Promise.all([load(), loadNurses()])
})

async function load() {
  loading.value = true
  try {
    const res = await http.get('/api/institution-admin/schedule-overview')
    overview.value = res.data
  } finally {
    loading.value = false
  }
}

async function loadNurses() {
  const res = await http.get('/api/institution-admin/nurses', {
    params: { institutionId: auth.institutionId },
  })
  nurses.value = res.data
}

function openCreate() {
  Object.assign(form, {
    shiftId: null,
    nurseId: nurses.value[0]?.nurseId ?? null,
    shiftDate: '',
    startTime: '',
    endTime: '',
    maxAppointments: 20,
    status: 1,
  })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function onSave() {
  if (!form.nurseId || !form.shiftDate || !form.startTime || !form.endTime || !form.maxAppointments) {
    ElMessage.warning('请填写完整班次信息')
    return
  }
  saving.value = true
  const payload = {
    nurseId: form.nurseId,
    shiftDate: form.shiftDate,
    startTime: form.startTime,
    endTime: form.endTime,
    maxAppointments: form.maxAppointments,
    status: form.status,
  }
  try {
    if (form.shiftId) {
      await http.put(`/api/institution-admin/shifts/${form.shiftId}`, payload)
    } else {
      await http.post('/api/institution-admin/shifts', payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } catch {
    /* 错误提示由拦截器统一处理 */
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除 ${row.shiftDate} ${row.startTime}-${row.endTime} 的班次吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await http.delete(`/api/institution-admin/shifts/${row.shiftId}`)
    ElMessage.success('已删除')
    await load()
  } catch {
    /* 错误提示由拦截器统一处理 */
  }
}
</script>

<style scoped>
.block-card {
  margin-bottom: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.normal {
  color: #67c23a;
  font-size: 13px;
}
</style>

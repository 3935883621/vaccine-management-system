<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>供货记录</span>
        <el-button type="primary" @click="openCreate">创建供货</el-button>
      </div>
    </template>
    <el-table :data="supplies" v-loading="loading">
      <el-table-column prop="vaccineName" label="疫苗" width="140" />
      <el-table-column prop="batchNo" label="批次号" width="170" />
      <el-table-column prop="institutionName" label="收货机构" width="180" />
      <el-table-column prop="supplyDate" label="供货日期" width="110" />
      <el-table-column label="数量" width="140">
        <template #default="{ row }">{{ row.receivedQuantity }} / {{ row.quantity }} 支</template>
      </el-table-column>
      <el-table-column prop="expiryDate" label="有效期至" width="110" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'primary' : 'success'" size="small">
            {{ row.status === 1 ? '待入库' : '已入库' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" title="创建供货" width="480px">
    <el-form :model="form" label-width="100px">
      <el-form-item label="生产批次" required>
        <el-select v-model="form.batchId" style="width: 100%" @change="onBatchChange">
          <el-option
            v-for="b in batches"
            :key="b.batchId"
            :label="`${b.batchNo}（${b.vaccineName}，有效期 ${b.expiryDate}）`"
            :value="b.batchId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="收货机构" required>
        <el-select v-model="form.institutionId" style="width: 100%">
          <el-option v-for="i in institutions" :key="i.institutionId" :label="i.name" :value="i.institutionId" />
        </el-select>
      </el-form-item>
      <el-form-item label="供货数量" required>
        <el-input-number v-model="form.quantity" :min="1" :max="1000000" />
      </el-form-item>
      <el-form-item label="供货日期" required>
        <el-date-picker v-model="form.supplyDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'

const supplies = ref([])
const batches = ref([])
const institutions = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({ batchId: null, institutionId: null, quantity: 0, supplyDate: '' })

onMounted(async () => {
  await Promise.all([load(), loadBatches(), loadInstitutions()])
})

async function load() {
  loading.value = true
  try {
    const res = await http.get('/api/manufacturer/supplies')
    supplies.value = res.data
  } finally {
    loading.value = false
  }
}

async function loadBatches() {
  const res = await http.get('/api/manufacturer/batches')
  batches.value = res.data
}

async function loadInstitutions() {
  const res = await http.get('/api/manufacturer/institutions')
  institutions.value = res.data
}

function openCreate() {
  Object.assign(form, {
    batchId: batches.value[0]?.batchId ?? null,
    institutionId: institutions.value[0]?.institutionId ?? null,
    quantity: 100,
    supplyDate: new Date().toISOString().slice(0, 10),
  })
  dialogVisible.value = true
}

function onBatchChange(batchId) {
  const b = batches.value.find((x) => x.batchId === batchId)
  if (b) {
    form.quantity = b.productionQuantity
  }
}

async function onSave() {
  if (!form.batchId || !form.institutionId || !form.quantity || !form.supplyDate) {
    ElMessage.warning('请填写完整供货信息')
    return
  }
  saving.value = true
  try {
    await http.post('/api/manufacturer/supplies', { ...form })
    ElMessage.success('供货记录已创建')
    dialogVisible.value = false
    await load()
  } catch {
    /* 错误提示由拦截器统一处理 */
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>

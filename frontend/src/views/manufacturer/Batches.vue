<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>生产批次管理</span>
        <el-button type="primary" @click="openCreate">新增批次</el-button>
      </div>
    </template>
    <el-table :data="batches" v-loading="loading">
      <el-table-column prop="batchNo" label="批次号" width="180" />
      <el-table-column prop="vaccineName" label="疫苗" width="150" />
      <el-table-column prop="productionDate" label="生产日期" width="110" />
      <el-table-column prop="expiryDate" label="有效期至" width="110" />
      <el-table-column prop="productionQuantity" label="生产数量" width="100" />
      <el-table-column label="操作" min-width="140" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="form.batchId ? '编辑批次' : '新增批次'" width="480px">
    <el-form :model="form" label-width="100px">
      <el-form-item label="疫苗" required>
        <el-select v-model="form.vaccineId" style="width: 100%">
          <el-option v-for="v in myVaccines" :key="v.vaccineId" :label="v.name" :value="v.vaccineId" />
        </el-select>
      </el-form-item>
      <el-form-item label="批次号" required><el-input v-model="form.batchNo" /></el-form-item>
      <el-form-item label="生产日期" required>
        <el-date-picker v-model="form.productionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="有效期至" required>
        <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="生产数量" required>
        <el-input-number v-model="form.productionQuantity" :min="0" :max="1000000" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'

const batches = ref([])
const myVaccines = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({
  batchId: null,
  vaccineId: null,
  batchNo: '',
  productionDate: '',
  expiryDate: '',
  productionQuantity: 0,
})

onMounted(async () => {
  await Promise.all([load(), loadMyVaccines()])
})

async function load() {
  loading.value = true
  try {
    const res = await http.get('/api/manufacturer/batches')
    batches.value = res.data
  } finally {
    loading.value = false
  }
}

async function loadMyVaccines() {
  const res = await http.get('/api/manufacturer/vaccines')
  myVaccines.value = res.data.filter((v) => v.mine)
}

function openCreate() {
  Object.assign(form, {
    batchId: null,
    vaccineId: myVaccines.value[0]?.vaccineId ?? null,
    batchNo: '',
    productionDate: '',
    expiryDate: '',
    productionQuantity: 0,
  })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function onSave() {
  if (!form.vaccineId || !form.batchNo || !form.productionDate || !form.expiryDate) {
    ElMessage.warning('请填写完整批次信息')
    return
  }
  saving.value = true
  const payload = {
    vaccineId: form.vaccineId,
    batchNo: form.batchNo,
    productionDate: form.productionDate,
    expiryDate: form.expiryDate,
    productionQuantity: form.productionQuantity,
  }
  try {
    if (form.batchId) {
      await http.put(`/api/manufacturer/batches/${form.batchId}`, payload)
    } else {
      await http.post('/api/manufacturer/batches', payload)
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
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>

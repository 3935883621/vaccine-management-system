<template>
  <el-card shadow="never" header="供货入库">
    <el-table :data="supplies" v-loading="loading">
      <el-table-column prop="vaccineName" label="疫苗" width="140" />
      <el-table-column prop="batchNo" label="批次号" width="170" />
      <el-table-column prop="manufacturerName" label="生产商" width="170" />
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
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" size="small" type="primary" plain @click="openStockIn(row)">
            入库
          </el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" title="确认入库" width="420px">
    <p class="tips">
      批次 {{ form.batchNo }}，供货总量 {{ form.quantity }} 支，已入库 {{ form.receivedQuantity }} 支，
      本次最多可入库 {{ form.quantity - form.receivedQuantity }} 支。
    </p>
    <el-form label-width="90px">
      <el-form-item label="入库数量" required>
        <el-input-number v-model="form.stockInQty" :min="1" :max="Math.max(1, form.quantity - form.receivedQuantity)" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onStockIn">确认入库</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'

const supplies = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({ supplyId: null, batchNo: '', quantity: 0, receivedQuantity: 0, stockInQty: 1 })

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await http.get('/api/institution-admin/supplies')
    supplies.value = res.data
  } finally {
    loading.value = false
  }
}

function openStockIn(row) {
  Object.assign(form, {
    supplyId: row.supplyId,
    batchNo: row.batchNo,
    quantity: row.quantity,
    receivedQuantity: row.receivedQuantity,
    stockInQty: row.quantity - row.receivedQuantity,
  })
  dialogVisible.value = true
}

async function onStockIn() {
  saving.value = true
  try {
    await http.post(`/api/institution-admin/supplies/${form.supplyId}/stock-in`, {
      quantity: form.stockInQty,
    })
    ElMessage.success('入库成功')
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
.tips {
  margin-bottom: 14px;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
}
</style>

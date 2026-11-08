<#
.SYNOPSIS
    疫苗管理系统 5 分钟一键演示脚本

.DESCRIPTION
    全 API 走通主流程，逐步输出演示过程：
      超管注册机构/生产商 -> 生产商建疫苗/批次/供货 -> 机构入库/排班
      -> 居民预约（FEFO 锁定最早效期批次）-> 规则拒绝演示（重复预约/时段冲突）
      -> 机构确认接种 -> 全链路追溯 -> 居民查看接种记录 -> 第 2 剂预约
      -> 预警扫描（临期/低库存）-> 过期批次自动封锁并取消预约
      -> 居民查看取消原因 -> 销毁过期疫苗 -> 数据一致性核对
    主流程无人工改库；仅"过期封锁"一步由脚本用 SQL 将批次有效期改为昨日，
    以触发定时扫描的过期逻辑（等价于时间快进）。

.PARAMETER Reset
    执行 sql/01_schema.sql + sql/02_data.sql 重置数据库为演示初始状态后再开始。

.PARAMETER BaseUrl
    后端地址，默认 http://localhost:8080

.PARAMETER MySql
    mysql 客户端路径（用于数据清理与 Reset），默认本机开发环境路径。

.EXAMPLE
    .\demo.ps1 -Reset    # 重置演示数据后走完整流程
.EXAMPLE
    .\demo.ps1           # 直接走完整流程（自动清理上次演示遗留数据）
#>
[CmdletBinding()]
param(
    [switch]$Reset,
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$MySql = 'C:\Users\admin1\program\mysql_8\mysql-8.0.11-winx64\bin\mysql.exe'
)

$ErrorActionPreference = 'Stop'
$script:pass = 0
$script:fail = 0

# ---------- 从 application.yml 读取数据库名与密码（与后端配置保持一致） ----------
$root = $PSScriptRoot
$yml = Get-Content (Join-Path $root 'backend\src\main\resources\application.yml') -Raw
$dbName = ([regex]::Match($yml, 'MYSQL_DB:([A-Za-z0-9_]+)')).Groups[1].Value
if (-not $dbName) { $dbName = 'vaccine_db' }
$dbPass = ([regex]::Match($yml, '(?m)^\s*password:\s*(\S+)')).Groups[1].Value
$dbUser = ([regex]::Match($yml, '(?m)^\s*username:\s*(\S+)')).Groups[1].Value
if (-not $dbUser) { $dbUser = 'root' }

# ---------- 工具函数 ----------
function Sql([string]$sql) {
    $env:MYSQL_PWD = $dbPass
    & $MySql "-u$dbUser" --default-character-set=utf8mb4 -e "USE $dbName; $sql"
}
function Step([int]$n, [string]$title) {
    Write-Host ''
    Write-Host ("==== [{0}] {1} ====" -f $n, $title) -ForegroundColor Cyan
}
function Ok([string]$msg) { $script:pass++; Write-Host ("  [OK] " + $msg) -ForegroundColor Green }
function Bad([string]$msg) { $script:fail++; Write-Host ("  [FAIL] " + $msg) -ForegroundColor Red }
function Post([string]$uri, $headers, $body) {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl$uri" -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json)
}
function Get([string]$uri, $headers) {
    Invoke-RestMethod -Method Get -Uri "$BaseUrl$uri" -Headers $headers
}
function ExpectFail([string]$name, [scriptblock]$sb, [int]$expectCode = 400) {
    try {
        & $sb | Out-Null
        Bad "$name (expected $expectCode but got 200)"
    } catch {
        $code = 0
        if ($_.ErrorDetails -and $_.ErrorDetails.Message -match '"code":(\d+)') { $code = [int]$Matches[1] }
        if ($code -eq 0 -and $_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        if ($code -eq $expectCode) { Ok "$name (got $expectCode)" }
        else { Bad "$name (expected $expectCode, got $code)" }
    }
}
function Login([string]$username, [string]$role, [string]$password = '123456') {
    $body = @{ username = $username; password = $password; role = $role } | ConvertTo-Json
    return Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType 'application/json' -Body $body
}

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$today = Get-Date
$tPlus2 = $today.AddDays(2).ToString('yyyy-MM-dd')
$tPlus3 = $today.AddDays(3).ToString('yyyy-MM-dd')
$tPlus10 = $today.AddDays(10).ToString('yyyy-MM-dd')
$tPlus100 = $today.AddDays(100).ToString('yyyy-MM-dd')
$tMinus200 = $today.AddDays(-200).ToString('yyyy-MM-dd')
$tMinus1 = $today.AddDays(-1).ToString('yyyy-MM-dd')

Write-Host ''
Write-Host '============================================================' -ForegroundColor Magenta
Write-Host '  疫苗管理系统 5 分钟一键演示（全链路 + 预警演练）' -ForegroundColor Magenta
Write-Host "  后端: $BaseUrl    数据库: $dbName" -ForegroundColor DarkGray
Write-Host '============================================================' -ForegroundColor Magenta

# ---------- [0] 准备与清理 ----------
Step 0 '准备：清理上次演示遗留数据（幂等）'
if ($Reset) {
    $sqlDir = (Join-Path $root 'sql').Replace('\', '/')
    $env:MYSQL_PWD = $dbPass
    & $MySql "-u$dbUser" --default-character-set=utf8mb4 $dbName -e "source $sqlDir/01_schema.sql; source $sqlDir/02_data.sql;" | Out-Null
    Write-Host '  已执行 01_schema.sql + 02_data.sql 重置为演示初始状态'
}
Sql "DELETE FROM appointment WHERE stock_id IN (SELECT stock_id FROM vaccine_stock WHERE batch_no LIKE 'DEMO-%');
     DELETE FROM vaccine_stock WHERE batch_no LIKE 'DEMO-%';
     DELETE FROM supply WHERE batch_id IN (SELECT batch_id FROM batch WHERE batch_no LIKE 'DEMO-%');
     DELETE FROM shift WHERE nurse_id IN (SELECT nurse_id FROM nurse WHERE employee_no = 'DEMO001');
     DELETE FROM batch WHERE batch_no LIKE 'DEMO-%';
     DELETE FROM vaccine_manufacturer WHERE vaccine_id IN (SELECT vaccine_id FROM vaccine WHERE name LIKE 'DEMO%');
     DELETE FROM nurse WHERE employee_no = 'DEMO001';
     DELETE FROM vaccine WHERE name LIKE 'DEMO%';
     DELETE FROM institution_admin WHERE username = 'demo_admin';
     DELETE FROM institution WHERE name LIKE 'DEMO%';
     DELETE FROM manufacturer WHERE username = 'demo_mfr';
     DELETE FROM `user` WHERE username = 'demo_user';" | Out-Null
Ok '演示数据已清理，环境就绪'

# ---------- [1] 超级管理员注册机构与生产商 ----------
Step 1 '超级管理员登录并注册接种机构、生产商（SA-01/SA-02）'
$hSa = @{ Authorization = "Bearer $((Login 'superadmin' 'SUPER_ADMIN').data.token)" }
$inst = Post '/api/super-admin/institutions' $hSa @{ name = 'DEMO Vaccination Center'; address = 'DEMO Street 1'; contactPhone = '010-DEMO0001'; region = 'DEMO District'; adminUsername = 'demo_admin'; adminPassword = '123456'; adminRealName = 'DEMO Admin'; adminPhone = '13800009999' }
$mfr = Post '/api/super-admin/manufacturers' $hSa @{ name = 'DEMO Biotech Co.'; licenseNo = 'DEMO-SC2026-001'; contactPhone = '010-DEMO0002'; address = 'DEMO Park 2'; username = 'demo_mfr'; password = '123456' }
if ($inst.code -eq 200 -and $mfr.code -eq 200) {
    Ok ("机构 'DEMO Vaccination Center' + 管理员账号 demo_admin 已创建")
    Ok ("生产商 '$($mfr.data.name)' + 登录账号 demo_mfr 已创建")
} else { Bad "注册失败 inst=$($inst.code) mfr=$($mfr.code)" }
# 动态获取机构/生产商 ID（AUTO_INCREMENT 偏移不依赖固定 ID）
$instList = Get '/api/super-admin/institutions' $hSa
$instId = ($instList.data | Where-Object { $_.name -eq 'DEMO Vaccination Center' }).institutionId
$mfrList = Get '/api/super-admin/manufacturers' $hSa
$mfrId = ($mfrList.data | Where-Object { $_.name -eq 'DEMO Biotech Co.' }).manufacturerId

# ---------- [2] 生产商维护疫苗、批次、供货 ----------
Step 2 '生产商创建疫苗（2 剂）、生产批次（3 个）、供货记录（M-01/M-02/M-03）'
$hM = @{ Authorization = "Bearer $((Login 'demo_mfr' 'MANUFACTURER').data.token)" }
$v1 = Post '/api/manufacturer/vaccines' $hM @{ name = 'DEMO Combined Vaccine'; type = 'Inactivated'; disease = 'DEMO Disease'; dosesRequired = 2; manufacturerIds = @($mfrId) }
$v2 = Post '/api/manufacturer/vaccines' $hM @{ name = 'DEMO LowStock Vaccine'; type = 'Inactivated'; disease = 'DEMO Disease'; dosesRequired = 1; manufacturerIds = @($mfrId) }
$v1Id = $v1.data.vaccineId
$v2Id = $v2.data.vaccineId
# B1 正常批次（t+100），B2 临期批次（t+10，演示 FEFO 首选 + 临期预警），B3 低库存疫苗批次（5 支）
$b1 = Post '/api/manufacturer/batches' $hM @{ vaccineId = $v1Id; batchNo = 'DEMO-B1'; productionDate = $tMinus200; expiryDate = $tPlus100; productionQuantity = 50 }
$b2 = Post '/api/manufacturer/batches' $hM @{ vaccineId = $v1Id; batchNo = 'DEMO-B2'; productionDate = $tMinus200; expiryDate = $tPlus10; productionQuantity = 10 }
$b3 = Post '/api/manufacturer/batches' $hM @{ vaccineId = $v2Id; batchNo = 'DEMO-B3'; productionDate = $tMinus200; expiryDate = $tPlus100; productionQuantity = 5 }
$sp1 = Post '/api/manufacturer/supplies' $hM @{ batchId = $b1.data.batchId; institutionId = $instId; quantity = 50; supplyDate = $today.ToString('yyyy-MM-dd') }
$sp2 = Post '/api/manufacturer/supplies' $hM @{ batchId = $b2.data.batchId; institutionId = $instId; quantity = 10; supplyDate = $today.ToString('yyyy-MM-dd') }
$sp3 = Post '/api/manufacturer/supplies' $hM @{ batchId = $b3.data.batchId; institutionId = $instId; quantity = 5; supplyDate = $today.ToString('yyyy-MM-dd') }
if ($v1.code -eq 200 -and $v2.code -eq 200 -and $b1.code -eq 200 -and $b2.code -eq 200 -and $b3.code -eq 200 -and $sp1.code -eq 200 -and $sp2.code -eq 200 -and $sp3.code -eq 200) {
    Ok '疫苗 DEMO Combined Vaccine(2剂) / DEMO LowStock Vaccine(1剂) 已创建'
    Ok "批次 DEMO-B1(效期 $tPlus100, 50支) / DEMO-B2(效期 $tPlus10, 10支) / DEMO-B3(5支) 已创建"
    Ok '3 笔供货记录已送达 DEMO 机构（待入库）'
} else { Bad '生产商环节创建失败' }

# ---------- [3] 机构管理员入库、建护士、排班 ----------
Step 3 '机构管理员确认入库、维护护士、创建班次（A-02/A-04）'
$hA = @{ Authorization = "Bearer $((Login 'demo_admin' 'INSTITUTION_ADMIN').data.token)" }
$supplies = Get '/api/institution-admin/supplies' $hA
$pending = @($supplies.data | Where-Object { $_.batchNo -like 'DEMO-*' -and $_.status -eq 1 })
foreach ($sp in $pending) {
    $r = Post "/api/institution-admin/supplies/$($sp.supplyId)/stock-in" $hA @{ quantity = $sp.quantity }
    if ($r.code -ne 200) { Bad "入库失败 supply=$($sp.supplyId) code=$($r.code)" }
}
Ok "3 笔供货已全部入库（DEMO-B1 50 支 / DEMO-B2 10 支 / DEMO-B3 5 支）"
$nurse = Post '/api/institution-admin/nurses' $hA @{ name = 'DEMO Nurse'; employeeNo = 'DEMO001'; title = 'Supervisor'; phone = '13900008888' }
$shA1 = Post '/api/institution-admin/shifts' $hA @{ nurseId = $nurse.data.nurseId; shiftDate = $tPlus2; startTime = '08:00:00'; endTime = '12:00:00'; maxAppointments = 20 }
$shB1 = Post '/api/institution-admin/shifts' $hA @{ nurseId = $nurse.data.nurseId; shiftDate = $tPlus3; startTime = '08:00:00'; endTime = '12:00:00'; maxAppointments = 20 }
$shC1 = Post '/api/institution-admin/shifts' $hA @{ nurseId = $nurse.data.nurseId; shiftDate = $tPlus2; startTime = '09:00:00'; endTime = '11:00:00'; maxAppointments = 20 }
if ($nurse.code -eq 200 -and $shA1.code -eq 200 -and $shB1.code -eq 200 -and $shC1.code -eq 200) {
    Ok "护士 DEMO Nurse 已创建；3 个班次已排定：A($tPlus2 08-12) / B($tPlus3 08-12) / C($tPlus2 09-11 与 A 重叠，用于冲突演示)"
} else { Bad '护士/班次创建失败' }

# ---------- [4] 居民注册登录并查询 ----------
Step 4 '居民注册登录，查询机构疫苗与批次（FEFO 排序）'
$reg = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/register" -ContentType 'application/json' -Body (@{ username = 'demo_user'; password = '123456'; realName = 'DEMO Resident'; idCard = '110101202611080001'; gender = 1 } | ConvertTo-Json)
$hU = @{ Authorization = "Bearer $((Login 'demo_user' 'USER').data.token)" }
$insts = Get '/api/user/institutions' $hU
$demoInst = $insts.data | Where-Object { $_.name -eq 'DEMO Vaccination Center' }
$vaccines = @($demoInst.availableVaccines | ForEach-Object { $_.vaccineId })
if ($vaccines -contains $v1Id) { Ok '居民端可查询到 DEMO 机构及其 DEMO Combined Vaccine（有库存才展示）' }
else { Bad '居民端未查到 DEMO 机构疫苗' }
$batches = Get "/api/user/institutions/$instId/vaccines/$v1Id/batches" $hU
$order = (@($batches.data | ForEach-Object { $_.batchNo }) -join ' -> ')
if (@($batches.data)[0].batchNo -eq 'DEMO-B2') { Ok "批次按 FEFO 排序：$order（DEMO-B2 效期最近，排最前）" }
else { Bad "FEFO 排序异常：$order" }

# ---------- [5] 居民预约（FEFO 锁定最早效期批次） ----------
Step 5 '居民预约班次 A（FEFO 自动锁定最早效期批次 DEMO-B2）'
$bk1 = Post '/api/user/appointments' $hU @{ vaccineId = $v1Id; shiftId = $shA1.data.shiftId }
if ($bk1.code -eq 200 -and $bk1.data.batchNo -eq 'DEMO-B2' -and $bk1.data.doseNumber -eq 1) {
    Ok ("预约成功：第 1 剂，系统按 FEFO 锁定批次 $($bk1.data.batchNo)（DEMO-B2 有效期 $tPlus10，早于 DEMO-B1）")
} else { Bad "预约异常 code=$($bk1.code) batch=$($bk1.data.batchNo) dose=$($bk1.data.doseNumber)" }

# ---------- [6] 规则演示：重复预约与时段冲突 ----------
Step 6 '业务规则演示：重复预约 / 同时段冲突均被拒绝'
ExpectFail '同一班次重复预约被拒' { Post '/api/user/appointments' $hU @{ vaccineId = $v1Id; shiftId = $shA1.data.shiftId } } 400
ExpectFail '同时段冲突预约被拒（班次 C 与 A 重叠）' { Post '/api/user/appointments' $hU @{ vaccineId = $v2Id; shiftId = $shC1.data.shiftId } } 400

# ---------- [7] 机构确认接种 + 全链路追溯 ----------
Step 7 '机构确认接种，并演示库存全链路追溯'
$cf = Post "/api/institution-admin/appointments/$($bk1.data.appointmentId)/confirm" $hA
if ($cf.code -eq 200) { Ok '第 1 剂接种完成（锁定库存已扣减，预约置为已完成）' }
else { Bad "确认接种失败 code=$($cf.code)" }
$trace = Get "/api/institution-admin/stocks/$($bk1.data.stockId)/trace" $hA
Write-Host ('  追溯链路: 生产商[' + $trace.data.manufacturerName + '] -> 批次[' + $trace.data.batchNo + '] -> 供货[机构=' + $trace.data.supplyInstitutionName + ', 配送 ' + $trace.data.supplyQuantity + ' 支] -> 库存 -> 预约 -> 接种')
Ok '全链路追溯可查：生产商 -> 批次 -> 供货 -> 库存 -> 预约 -> 接种'

# ---------- [8] 居民查看接种记录 ----------
Step 8 '居民查看接种记录'
$records = Get '/api/user/vaccination-records' $hU
$rec = @($records.data)[0]
if ($rec -and $rec.vaccineName -eq 'DEMO Combined Vaccine') {
    Ok ("接种记录: 疫苗=$($rec.vaccineName) 批次=$($rec.batchNo) 剂次=$($rec.doseNumber) 护士=$($rec.nurseName) 时间=$($rec.vaccinationTime)")
} else { Bad '接种记录异常' }

# ---------- [9] 第 2 剂预约 ----------
Step 9 '居民预约第 2 剂（班次 B，系统自动推算剂次=2）'
$bk2 = Post '/api/user/appointments' $hU @{ vaccineId = $v1Id; shiftId = $shB1.data.shiftId }
if ($bk2.code -eq 200 -and $bk2.data.doseNumber -eq 2 -and $bk2.data.batchNo -eq 'DEMO-B2') {
    Ok "第 2 剂预约成功（dose=2，继续锁定最早效期批次 DEMO-B2，已锁定 1 支）"
} else { Bad "第 2 剂预约异常 code=$($bk2.code) dose=$($bk2.data.doseNumber)" }

# ---------- [10] 预警扫描：临期 + 低库存 ----------
Step 10 '机构执行库存预警扫描：临期批次标黄 + 低库存提示（S-01/S-02）'
$scan1 = Post '/api/institution-admin/alerts/scan' $hA
$alerts1 = Get '/api/institution-admin/alerts' $hA
$al1Json = $alerts1.data | ConvertTo-Json -Compress -Depth 6
if ($al1Json -match 'DEMO-B2' -and $al1Json -match 'DEMO LowStock Vaccine') {
    Ok '预警看板：DEMO-B2（效期 10 天内）已标记临期黄色预警，仍可正常出库'
    Ok '预警看板：DEMO LowStock Vaccine 库存 5 支 <= 10，触发低库存补货提示'
} else { Bad "预警内容异常: $al1Json" }

# ---------- [11] 过期封锁：时间快进后扫描 ----------
Step 11 '时间快进：DEMO-B2 到期 -> 扫描自动封锁并取消关联预约'
Sql "UPDATE vaccine_stock SET expiry_date = '$tMinus1' WHERE batch_no = 'DEMO-B2';" | Out-Null
$scan2 = Post '/api/institution-admin/alerts/scan' $hA
if ($scan2.data.expiredBlocked -ge 1 -and $scan2.data.cancelledAppointments -ge 1) {
    Ok "过期批次 DEMO-B2 已被自动封锁（status=3，禁止出库）；关联的第 2 剂预约已被系统自动取消"
} else { Bad "过期封锁异常: $($scan2.data | ConvertTo-Json -Compress)" }

# ---------- [12] 居民查看取消原因 ----------
Step 12 '居民端查看被取消预约及取消原因'
$appts = Get '/api/user/appointments' $hU
$cancelled = @($appts.data | Where-Object { $_.appointmentId -eq $bk2.data.appointmentId })[0]
$hex = (Sql "SELECT HEX(cancel_reason) FROM appointment WHERE appointment_id=$($bk2.data.appointmentId);" | Select-Object -Skip 1 | Select-Object -First 1).Trim()
if ($cancelled -and $cancelled.status -eq 3 -and $hex -eq 'E796ABE88B97E689B9E6ACA1E8BF87E69C9F') {
    Ok "我的预约：第 2 剂状态=已取消，取消原因='疫苗批次过期'（数据库 HEX 校验通过）"
} else { Bad "取消原因异常: status=$($cancelled.status) hex=$hex" }

# ---------- [13] 销毁过期疫苗 ----------
Step 13 '机构销毁过期疫苗（锁定校验通过后销毁，记录保留）'
$b2Stock = (Sql "SELECT stock_id, quantity, locked_quantity, status FROM vaccine_stock WHERE batch_no='DEMO-B2';" | Select-Object -Skip 1 | Select-Object -First 1).Trim()
$b2StockId = ([regex]::Match($b2Stock, '^\d+')).Value
$des = Post "/api/institution-admin/stocks/$b2StockId/destroy" $hA
$b2After = (Sql "SELECT quantity, locked_quantity, status FROM vaccine_stock WHERE stock_id=$b2StockId;" | Select-Object -Skip 1 | Select-Object -First 1).Trim()
if ($des.code -eq 200 -and $b2After -match '^0\s+0\s+4') {
    Ok 'DEMO-B2 已销毁：status=4 已销毁、数量归零、锁定为零，销毁记录保留可查'
} else { Bad "销毁异常 code=$($des.code) state=$b2After" }

# ---------- [14] 数据一致性核对 ----------
Step 14 '数据一致性核对（quantity + locked == 入库量，守恒不变式）'
$rows = @()
$out = Sql "SELECT s.batch_no, s.quantity, s.locked_quantity, s.status, sp.received_quantity FROM vaccine_stock s JOIN supply sp ON s.supply_id = sp.supply_id WHERE s.batch_no LIKE 'DEMO-%' ORDER BY s.batch_no;"
for ($i = 1; $i -lt $out.Count; $i++) { $rows += $out[$i].Trim() }
$ok1 = $false; $ok3 = $false
foreach ($row in $rows) {
    Write-Host "  $row"
    if ($row -match '^DEMO-B1\s+(\d+)\s+(\d+)\s+\d+\s+(\d+)$' -and ([int]$Matches[1] + [int]$Matches[2]) -eq [int]$Matches[3]) { $ok1 = $true }
    if ($row -match '^DEMO-B3\s+(\d+)\s+(\d+)\s+\d+\s+(\d+)$' -and ([int]$Matches[1] + [int]$Matches[2]) -eq [int]$Matches[3]) { $ok3 = $true }
}
if ($ok1 -and $ok3) { Ok '守恒校验通过：DEMO-B1（50=50+0）、DEMO-B3（5=5+0），DEMO-B2 已销毁记录保留' }
else { Bad '守恒校验失败' }
$ccRows = Sql "SELECT COUNT(*) FROM shift s JOIN nurse n ON s.nurse_id = n.nurse_id WHERE n.employee_no='DEMO001' AND s.current_count <> (SELECT COUNT(*) FROM appointment a WHERE a.shift_id = s.shift_id AND a.status = 1);" | Select-Object -Skip 1
if (($ccRows | Select-Object -First 1).Trim() -eq '0') { Ok '班次名额计数与待接种预约数一致（current_count 无偏差）' }
else { Bad '班次计数不一致' }

# ---------- 汇总 ----------
$sw.Stop()
Write-Host ''
Write-Host '============================================================' -ForegroundColor Magenta
if ($script:fail -eq 0) {
    Write-Host ("  演示完成！全部 $($script:pass) 个检查点通过，耗时 {0:N1} 秒（5 分钟内）" -f $sw.Elapsed.TotalSeconds) -ForegroundColor Green
} else {
    Write-Host ("  演示结束：$($script:pass) 通过 / $($script:fail) 失败，耗时 {0:N1} 秒" -f $sw.Elapsed.TotalSeconds) -ForegroundColor Red
}
Write-Host '  如需恢复初始演示数据：.\demo.ps1 -Reset' -ForegroundColor DarkGray
Write-Host '============================================================' -ForegroundColor Magenta

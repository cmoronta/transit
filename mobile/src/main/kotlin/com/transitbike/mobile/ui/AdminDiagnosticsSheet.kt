package com.transitbike.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.transitbike.mobile.data.AdminQuotaSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDiagnosticsSheet(
    isAdmin: Boolean,
    quotaSnapshot: AdminQuotaSnapshot?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Admin Quota Diagnostics",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Badge(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(if (isAdmin) "ADMIN CLAIM VERIFIED" else "ACCESS RESTRICTED")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isAdmin) {
                Text(
                    text = "Access requires Firebase Auth token with { \"admin\": true } custom claim.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            } else if (quotaSnapshot == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Cloudflare Worker & Quota DO Telemetry", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(12.dp))

                        TelemetryRow("Monthly Used:", "${quotaSnapshot.monthlyUsed} / ${quotaSnapshot.monthlyLimit}")
                        TelemetryRow("Per-Minute Rate:", "${quotaSnapshot.minuteUsed} / ${quotaSnapshot.minuteLimit} req/min")
                        TelemetryRow("User Daily Limit:", "${quotaSnapshot.perUserDailyLimit} requests/day")
                        TelemetryRow("Billing Month:", quotaSnapshot.monthKey.ifBlank { "Current" })
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh Live DO Counters")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

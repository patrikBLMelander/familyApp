package se.kidquest.app.dashboard

import se.kidquest.app.i18n.Money
import se.kidquest.app.i18n.tr
import se.kidquest.app.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.ApiErrors
import se.kidquest.app.network.RecordExpenseRequest

@Composable
fun RecordExpenseDialog(
    currentBalance: Int,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    /** When set, the expense is recorded on behalf of this member (parent flow). */
    memberId: String? = null,
    childName: String? = null,
) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<se.kidquest.app.network.ExpenseCategoryResponse>>(emptyList()) }
    var loadingData by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loadingData = true
        error = null
        try {
            categories = withContext(Dispatchers.IO) {
                ApiClient.walletApi.getExpenseCategories()
            }
            if (categories.isNotEmpty()) {
                categoryId = categories.first().id
            }
        } catch (e: Exception) {
            error = ApiErrors.message(e, tr(R.string.expense_load_categories_failed))
        } finally {
            loadingData = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (childName != null) tr(R.string.expense_title_for, childName) else tr(R.string.expense_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (loadingData) {
                    Text(tr(R.string.expense_loading_categories))
                    return@Column
                }
                if (error != null) {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error)
                    return@Column
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() }; error = null },
                    label = { Text(tr(R.string.amount_label_required, Money.symbol())) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; error = null },
                    label = { Text(tr(R.string.expense_description_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (categories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(tr(R.string.expense_category), style = MaterialTheme.typography.labelMedium)
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoryId = cat.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = categoryId == cat.id,
                                onClick = { categoryId = cat.id },
                            )
                            Text(
                                text = "${cat.emoji?.let { "$it " } ?: ""}${cat.name}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountKr = amount.toIntOrNull() ?: 0
                    if (amountKr <= 0) {
                        error = tr(R.string.common_enter_amount)
                        return@TextButton
                    }
                    if (amountKr > currentBalance) {
                        error = tr(R.string.expense_only_have, Money.format(currentBalance))
                        return@TextButton
                    }
                    val cat = categoryId
                    if (categories.isNotEmpty() && cat == null) {
                        error = tr(R.string.expense_pick_category)
                        return@TextButton
                    }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val req = RecordExpenseRequest(
                                    amount = amountKr,
                                    description = description.ifBlank { null },
                                    categoryId = cat,
                                    savingsGoalAllocations = null,
                                )
                                if (memberId != null) {
                                    ApiClient.walletApi.recordExpenseForMember(memberId, req)
                                } else {
                                    ApiClient.walletApi.recordExpense(req)
                                }
                            }
                            onSuccess()
                        } catch (e: Exception) {
                            error = ApiErrors.message(e, tr(R.string.expense_failed))
                        } finally {
                            loading = false
                        }
                    }
                },
            ) {
                Text(if (loading) tr(R.string.common_saving) else tr(R.string.expense_pay))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(R.string.common_cancel))
            }
        },
    )
}

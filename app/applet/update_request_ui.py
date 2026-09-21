import os

path = "app/src/main/java/com/example/ui/screens/RequestDetailScreen.kt"
with open(path, "r") as f:
    content = f.read()

# 1. Add showEditDialog state
target_state = "    var showDeleteDialog by remember { mutableStateOf(false) }"
replacement_state = target_state + "\n    var showEditDialog by remember { mutableStateOf(false) }"
if target_state in content and "showEditDialog" not in content:
    content = content.replace(target_state, replacement_state, 1)

# 2. Add Edit button in RequestStatus.OPEN
target_open = """                                 RequestStatus.OPEN -> {
                                     Button("""

replacement_open = """                                 RequestStatus.OPEN -> {
                                     OutlinedButton(
                                         onClick = { showEditDialog = true },
                                         shape = RoundedCornerShape(12.dp),
                                         modifier = Modifier
                                             .fillMaxWidth()
                                             .heightIn(min = 44.dp)
                                             .testTag("edit_request_btn")
                                     ) {
                                         Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                         Spacer(modifier = Modifier.width(8.dp))
                                         Text("Edit Request Details (While OPEN)", textAlign = TextAlign.Center, fontSize = 12.sp)
                                     }
                                     Spacer(modifier = Modifier.height(8.dp))
                                     Button("""

if target_open in content and "Edit Request Details" not in content:
    content = content.replace(target_open, replacement_open, 1)

# 3. Add EditRequestDialog at bottom
target_dialog = "    if (showDeleteDialog) {"
replacement_dialog = """    if (showEditDialog) {
        EditRequestDialog(
            request = request,
            onDismiss = { showEditDialog = false },
            onSubmit = { updated ->
                viewModel.updateRequest(updated)
                showEditDialog = false
            },
            isProcessing = state.isProcessing
        )
    }

    if (showDeleteDialog) {"

if target_dialog in content and "EditRequestDialog" not in content:
    content = content.replace(target_dialog, replacement_dialog, 1)

with open(path, "w") as f:
    f.write(content)
print("REQUEST DETAIL SCREEN UPDATED SUCCESSFULLY")

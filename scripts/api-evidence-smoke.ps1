param(
    [string]$BaseUrl = "http://127.0.0.1:8099"
)

$ErrorActionPreference = "Stop"
$script:BaseUrl = $BaseUrl
$script:Checks = New-Object System.Collections.Generic.List[object]

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        $Body = $null
    )

    $request = [Net.HttpWebRequest]::Create("$script:BaseUrl$Path")
    $request.Method = $Method
    $request.AllowAutoRedirect = $false
    $request.Timeout = 30000

    if ($null -ne $Body) {
        $request.ContentType = "application/json"
        $bytes = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 20 -Compress))
        $request.ContentLength = $bytes.Length
        $stream = $request.GetRequestStream()
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Close()
    }

    try {
        $response = $request.GetResponse()
    } catch [Net.WebException] {
        $response = $_.Exception.Response
    }

    if ($null -eq $response) {
        return [pscustomobject]@{ Status = 0; Body = "No HTTP response"; Location = $null }
    }

    $reader = New-Object IO.StreamReader($response.GetResponseStream())
    $content = $reader.ReadToEnd()
    $reader.Close()
    $result = [pscustomobject]@{
        Status   = [int]$response.StatusCode
        Body     = $content
        Location = $response.Headers["Location"]
    }
    $response.Close()
    return $result
}

function Add-Check {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [int]$ExpectedStatus,
        $Body = $null,
        [scriptblock]$Assertion = $null
    )

    $response = Invoke-Api -Method $Method -Path $Path -Body $Body
    $passed = $response.Status -eq $ExpectedStatus
    $assertionFailure = if ($passed) { "" } else { "Expected HTTP $ExpectedStatus, received $($response.Status)" }
    if ($passed -and $Assertion) {
        try {
            & $Assertion $response
        } catch {
            $passed = $false
            $assertionFailure = $_.Exception.Message
        }
    }
    $script:Checks.Add([pscustomobject]@{
        Name           = $Name
        Method         = $Method
        Path           = $Path
        ExpectedStatus = $ExpectedStatus
        ActualStatus   = $response.Status
        Passed         = $passed
        AssertionFailure = $assertionFailure
        Location       = $response.Location
    }) | Out-Null
    return $response
}

function Read-Json {
    param(
        [Parameter(Mandatory = $true)]$Response
    )
    if ([string]::IsNullOrWhiteSpace($Response.Body)) {
        throw "Expected a JSON response body"
    }
    try {
        $json = ConvertFrom-Json -InputObject $Response.Body -ErrorAction Stop
        if ($json -is [Array]) {
            foreach ($item in $json) {
                Write-Output $item
            }
        } else {
            Write-Output $json
        }
    } catch {
        throw "Response body was not valid JSON"
    }
}

function Assert-ClientErrorResponse {
    param(
        $Response,
        [int]$ExpectedStatus
    )
    $errorBody = Read-Json $Response
    if ($errorBody.status -ne $ExpectedStatus) {
        throw "Error body status does not match HTTP $ExpectedStatus"
    }
    if (-not [string]::IsNullOrWhiteSpace($errorBody.title) -and
        -not [string]::IsNullOrWhiteSpace($errorBody.detail)) {
        return
    }
    if (-not [string]::IsNullOrWhiteSpace($errorBody.error) -and
        -not [string]::IsNullOrWhiteSpace($errorBody.timestamp) -and
        -not [string]::IsNullOrWhiteSpace($errorBody.path)) {
        return
    }
    throw "Error body is neither an RFC 7807 problem nor the Spring error shape"
}

$null = Add-Check "OpenAPI document" GET "/v3/api-docs" 200 -Assertion {
    param($r)
    $json = Read-Json $r
    if (-not $json.openapi.StartsWith("3.") -or $null -eq $json.paths) { throw "OpenAPI version or paths missing" }
}
$null = Add-Check "OpenAPI YAML document" GET "/v3/api-docs.yaml" 200 -Assertion {
    param($r)
    if ($r.Body -notmatch '(?m)^openapi:\s*3\.') { throw "OpenAPI YAML version missing" }
}
$null = Add-Check "Swagger UI" GET "/swagger-ui/index.html" 200 -Assertion {
    param($r)
    if ($r.Body -notmatch 'swagger-ui') { throw "Swagger UI markup missing" }
}
$null = Add-Check "Swagger UI redirect" GET "/swagger-ui.html" 302 -Assertion {
    param($r)
    if ($r.Location -notmatch '/swagger-ui/index\.html') { throw "Swagger UI redirect Location missing" }
}
$null = Add-Check "Swagger config" GET "/v3/api-docs/swagger-config" 200 -Assertion {
    param($r)
    $json = Read-Json $r
    if ($json.url -notmatch '/v3/api-docs') { throw "Swagger config does not reference OpenAPI" }
}
$null = Add-Check "Actuator discovery root" GET "/actuator" 200 -Assertion {
    param($r)
    if ($null -eq (Read-Json $r)._links) { throw "Actuator links missing" }
}
$null = Add-Check "Health" GET "/actuator/health" 200 -Assertion {
    param($r)
    if ((Read-Json $r).status -ne "UP") { throw "Health status is not UP" }
}
$null = Add-Check "Info" GET "/actuator/info" 200 -Assertion {
    param($r)
    $null = Read-Json $r
}
$null = Add-Check "Actuator metrics" GET "/actuator/metrics" 200 -Assertion {
    param($r)
    $json = Read-Json $r
    if (@($json.names) -notcontains "jvm.memory.used") { throw "Expected JVM memory metric is missing" }
}
$null = Add-Check "Actuator metric detail" GET "/actuator/metrics/jvm.memory.used" 200 -Assertion {
    param($r)
    $json = Read-Json $r
    if ($json.name -ne "jvm.memory.used" -or @($json.measurements).Count -lt 1) {
        throw "JVM memory metric details are incomplete"
    }
}
$null = Add-Check "Readiness group is not configured" GET "/actuator/health/readiness" 404
$null = Add-Check "Liveness group is not configured" GET "/actuator/health/liveness" 404
$null = Add-Check "Actuator env is not exposed" GET "/actuator/env" 404
$null = Add-Check "Actuator heapdump is not exposed" GET "/actuator/heapdump" 404

$alias = "ev" + (Get-Random -Minimum 100000 -Maximum 999999)
$created = Add-Check "Create URL" POST "/api/urls" 201 @{
    url   = "https://example.com/api-evidence"
    alias = $alias
} -Assertion {
    param($r)
    $json = Read-Json $r
    $createdAt = [DateTimeOffset]::Parse($json.createdAt)
    $expiresAt = [DateTimeOffset]::Parse($json.expiresAt)
    $defaultLifetimeDays = ($expiresAt - $createdAt).TotalDays
    if ($json.code -ne $alias -or $json.url -ne "https://example.com/api-evidence" -or
        $json.shortUrl -notlike "*/$alias" -or $defaultLifetimeDays -lt 29 -or $defaultLifetimeDays -gt 31) {
        throw "Created URL response fields do not match the request"
    }
}
$url = Read-Json $created
$code = $url.code
$statsBefore = Add-Check "URL stats before redirect" GET "/api/urls/$code/stats" 200 -Assertion {
    param($r)
    if ((Read-Json $r).totalClicks -ne 0) { throw "New URL should have zero clicks before redirect" }
}
$redirect = Add-Check "Redirect" GET "/$code" 302 -Assertion {
    param($r)
    if ($r.Location -ne "https://example.com/api-evidence") { throw "Redirect Location does not match the created destination" }
}
$null = Add-Check "Unknown short code" GET "/NoSuchCode" 404 -Assertion {
    param($r)
    Assert-ClientErrorResponse $r 404
}
$stats = Add-Check "URL stats after redirect" GET "/api/urls/$code/stats" 200 -Assertion {
    param($r)
    $json = Read-Json $r
    if ($json.totalClicks -ne 1 -or @($json.recentClicks).Count -ne 1) {
        throw "Click analytics did not record exactly one redirect"
    }
}
$null = Add-Check "Duplicate alias" POST "/api/urls" 409 @{
    url   = "https://example.com/duplicate"
    alias = $alias
} -Assertion {
    param($r)
    Assert-ClientErrorResponse $r 409
}
$null = Add-Check "Unsafe URL scheme" POST "/api/urls" 400 @{ url = "javascript:alert(1)" } -Assertion {
    param($r)
    Assert-ClientErrorResponse $r 400
}
$null = Add-Check "Missing required URL" POST "/api/urls" 400 @{ alias = "missingurl" } -Assertion {
    param($r)
    Assert-ClientErrorResponse $r 400
}
$null = Add-Check "Unknown URL stats" GET "/api/urls/NoSuchCode/stats" 404 -Assertion {
    param($r)
    Assert-ClientErrorResponse $r 404
}
$expiration = [DateTime]::UtcNow.AddSeconds(10).ToString("yyyy-MM-ddTHH:mm:ssZ")
$expires = Add-Check "Create expiring URL" POST "/api/urls" 201 @{
    url       = "https://example.com/temporary"
    expiresAt = $expiration
} -Assertion {
    param($r)
    $json = Read-Json $r
    if ([string]::IsNullOrWhiteSpace($json.code) -or $null -eq $json.expiresAt) {
        throw "Expiring URL response omitted its code or expiry"
    }
}
$expiredCode = (Read-Json $expires).code
Start-Sleep -Seconds 11
$null = Add-Check "Expired redirect" GET "/$expiredCode" 410 -Assertion {
    param($r)
    Assert-ClientErrorResponse $r 410
}

$null = Add-Check "Scenario catalog" GET "/api/scenarios" 200 -Assertion {
    param($r)
    $ids = @((Read-Json $r) | ForEach-Object { $_.id })
    foreach ($required in @("greenfield", "brownfield", "ambiguous")) {
        if ($ids -notcontains $required) { throw "Scenario catalog is missing a required scenario" }
    }
}
$null = Add-Check "Unknown scenario" POST "/api/scenarios/not-a-scenario/run" 400 @{
    executionMode = "SEQUENTIAL"
} -Assertion {
    param($r)
    Assert-ClientErrorResponse $r 400
}
$greenfield = Add-Check "Greenfield scenario run" POST "/api/scenarios/greenfield/run" 200 @{
    executionMode = "SEQUENTIAL"
} -Assertion {
    param($r)
    $json = Read-Json $r
    if ($json.scenarioId -ne "greenfield" -or $json.status -ne "SUCCEEDED" -or
        [string]::IsNullOrWhiteSpace($json.runId) -or @($json.run.tasks).Count -lt 1) {
        throw "Greenfield response does not show a completed persisted run"
    }
}
$greenfieldRunId = (Read-Json $greenfield).runId
$null = Add-Check "Scenario run state" GET "/api/runs/$greenfieldRunId" 200 -Assertion {
    param($r)
    $json = Read-Json $r
    if ($json.status -ne "SUCCEEDED" -or @($json.tasks).Count -lt 1) {
        throw "Persisted greenfield run is not successful"
    }
}
$null = Add-Check "Scenario audit history" GET "/api/runs/$greenfieldRunId/audit" 200 -Assertion {
    param($r)
    $json = Read-Json $r
    if ($json.runId -ne $greenfieldRunId -or @($json.events).Count -lt 1) { throw "Persisted audit events are missing" }
}
$null = Add-Check "Scenario audit verification" GET "/api/runs/$greenfieldRunId/audit/verify" 200 -Assertion {
    param($r)
    if (-not (Read-Json $r).valid) { throw "Scenario audit chain did not verify" }
}
$null = Add-Check "Scenario pending approvals" GET "/api/runs/$greenfieldRunId/approvals/pending" 200 -Assertion {
    param($r)
    if (@(Read-Json $r).Count -ne 0) { throw "Greenfield run unexpectedly has pending approvals" }
}

$invalidRun = Add-Check "Run creation rejects empty task list" POST "/api/runs" 400 @{
    scenarioId = "api-evidence-invalid"
    tasks      = @()
} -Assertion {
    param($r)
    Assert-ClientErrorResponse $r 400
}
$missingRun = Add-Check "Unknown run state" GET "/api/runs/00000000-0000-0000-0000-000000000000" 404 -Assertion {
    param($r)
    Assert-ClientErrorResponse $r 404
}

$run = Add-Check "Create run" POST "/api/runs" 201 @{
    scenarioId = "api-evidence"
    inputSummary = "Local endpoint inventory"
    tasks = @(@{
        taskId       = "requirements"
        kind         = "REQUIREMENTS"
        dependencyIds = @()
        impactLevel  = "LOW"
    })
} -Assertion {
    param($r)
    $json = Read-Json $r
    if ($json.status -ne "CREATED" -or [string]::IsNullOrWhiteSpace($json.runId) -or
        @($json.tasks).Count -ne 1 -or $json.tasks[0].taskId -ne "requirements") {
        throw "Run creation did not persist the requested task graph"
    }
}
$runId = (Read-Json $run).runId
$null = Add-Check "Run details" GET "/api/runs/$runId" 200 -Assertion {
    param($r)
    if ((Read-Json $r).status -ne "CREATED") { throw "New run did not remain in CREATED state" }
}
$null = Add-Check "Run audit history" GET "/api/runs/$runId/audit" 200 -Assertion {
    param($r)
    if (@((Read-Json $r).events).Count -lt 1) { throw "Run creation audit event is missing" }
}
$null = Add-Check "Run audit verification" GET "/api/runs/$runId/audit/verify" 200 -Assertion {
    param($r)
    if (-not (Read-Json $r).valid) { throw "Run audit chain did not verify" }
}
$null = Add-Check "Run approvals list" GET "/api/runs/$runId/approvals/pending" 200 -Assertion {
    param($r)
    if (@(Read-Json $r).Count -ne 0) { throw "New run unexpectedly has pending approvals" }
}
$null = Add-Check "Execute run" POST "/api/runs/$runId/execute" 200 @{ mode = "SEQUENTIAL" } -Assertion {
    param($r)
    $json = Read-Json $r
    if ($json.status -ne "SUCCEEDED" -or $json.taskCounts.SUCCEEDED -ne 1) {
        throw "Execution response does not show the requested task succeeded"
    }
}
$null = Add-Check "Replan completed run" POST "/api/runs/$runId/replan" 200 @{
    updatedInputSummary = "Revised local inventory"
    tasks = @(@{
        taskId        = "requirements-v2"
        kind          = "REQUIREMENTS"
        dependencyIds = @()
        impactLevel   = "LOW"
    })
    requestedBy = "api-evidence"
    rationale   = "Exercise the documented replan operation"
} -Assertion {
    param($r)
    $json = Read-Json $r
    if ($json.runId -ne $runId -or @($json.addedTaskIds) -notcontains "requirements-v2") {
        throw "Replan response does not identify the added task"
    }
}
$null = Add-Check "Replanned task graph is persisted" GET "/api/runs/$runId" 200 -Assertion {
    param($r)
    $persistedRun = Read-Json $r
    $tasks = @($persistedRun.tasks)
    $replannedTask = @($tasks | Where-Object taskId -eq "requirements-v2")
    if ($replannedTask.Count -ne 1) {
        throw "Replanned task is absent from the persisted run"
    }
    if ($persistedRun.status -ne "CREATED" -or $replannedTask[0].state -ne "PENDING") {
        throw "Replan did not return the run to CREATED with the added task pending"
    }
}

$safeStopRun = Add-Check "Create safe-stop run" POST "/api/runs" 201 @{
    scenarioId = "api-evidence-safe-stop"
    tasks = @(@{
        taskId        = "requirements"
        kind          = "REQUIREMENTS"
        dependencyIds = @()
        impactLevel   = "LOW"
    })
} -Assertion {
    param($r)
    if ((Read-Json $r).status -ne "CREATED") { throw "Safe-stop test run was not created" }
}
$safeStopRunId = (Read-Json $safeStopRun).runId
$null = Add-Check "Request safe-stop" POST "/api/runs/$safeStopRunId/safe-stop" 200 -Assertion {
    param($r)
    if ((Read-Json $r).runId -ne $safeStopRunId) { throw "Safe-stop response run ID mismatch" }
}
$null = Add-Check "Execute safe-stopped run" POST "/api/runs/$safeStopRunId/execute" 200 @{ mode = "SEQUENTIAL" } -Assertion {
    param($r)
    if ((Read-Json $r).status -ne "SAFE_STOPPED") { throw "Execution did not stop at the requested safe-stop" }
}
$null = Add-Check "Resume safe-stopped run" POST "/api/runs/$safeStopRunId/resume" 200 @{ mode = "SEQUENTIAL" } -Assertion {
    param($r)
    if ((Read-Json $r).status -ne "SUCCEEDED") { throw "Resumed safe-stop run did not succeed" }
}
$null = Add-Check "Safe-stop run state persisted" GET "/api/runs/$safeStopRunId" 200 -Assertion {
    param($r)
    if ((Read-Json $r).status -ne "SUCCEEDED") { throw "Persisted safe-stop run is not successful" }
}

$brownfield = Add-Check "Brownfield run creates approval" POST "/api/scenarios/brownfield/run" 200 @{
    executionMode = "SEQUENTIAL"
} -Assertion {
    param($r)
    $json = Read-Json $r
    if ($json.status -ne "AWAITING_APPROVAL" -or @($json.pendingApprovals).Count -lt 1) {
        throw "Brownfield run did not pause at its approval checkpoint"
    }
}
$brownfieldRunId = (Read-Json $brownfield).runId
$pendingResponse = Add-Check "Brownfield pending approval list" GET "/api/runs/$brownfieldRunId/approvals/pending" 200 -Assertion {
    param($r)
    $approvals = @(Read-Json $r)
    if ($approvals.Count -lt 1 -or $approvals[0].status -ne "PENDING") {
        throw "No persisted pending approval was returned"
    }
}
$pendingApprovals = @(Read-Json $pendingResponse)
$pendingApproval = $pendingApprovals[0]
$null = Add-Check "Blank approver is rejected" POST "/api/approvals/$($pendingApproval.approvalId)" 400 @{
    decision  = "APPROVE"
    approver  = " "
    rationale = "Validation boundary"
} -Assertion {
    param($r)
    Assert-ClientErrorResponse $r 400
}
$null = Add-Check "Replan is blocked while approval is pending" POST "/api/runs/$brownfieldRunId/replan" 409 @{
    updatedInputSummary = "This must wait for approval"
    tasks = @(@{
        taskId        = "requirements-v2"
        kind          = "REQUIREMENTS"
        dependencyIds = @()
        impactLevel   = "LOW"
    })
    requestedBy = "api-evidence"
    rationale   = "Verify approval state guard"
} -Assertion {
    param($r)
    Assert-ClientErrorResponse $r 409
}
$approvalRounds = 0
$pendingForApproval = $pendingApprovals
while ($pendingForApproval.Count -gt 0 -and $approvalRounds -lt 8) {
    foreach ($approval in $pendingForApproval) {
        $approvalId = $approval.approvalId
        $null = Add-Check "Approve brownfield checkpoint $($approvalRounds + 1)" POST "/api/approvals/$approvalId" 200 @{
            decision  = "APPROVE"
            approver  = "api-evidence-reviewer"
            rationale = "Reviewed the local assessment change"
        } -Assertion {
            param($r)
            $decision = Read-Json $r
            if ($decision.status -ne "APPROVED" -or $decision.approver -ne "api-evidence-reviewer") {
                throw "Approval response does not show the expected human decision"
            }
        }
    }
    $resume = Add-Check "Resume approved brownfield run $($approvalRounds + 1)" POST "/api/runs/$brownfieldRunId/resume" 200 @{
        mode = "SEQUENTIAL"
    } -Assertion {
        param($r)
        $status = (Read-Json $r).status
        if ($status -notin @("AWAITING_APPROVAL", "SUCCEEDED")) {
            throw "Resume returned an unexpected lifecycle state"
        }
    }
    $pendingForApproval = @(Add-Check "Brownfield pending approvals after resume $($approvalRounds + 1)" `
            GET "/api/runs/$brownfieldRunId/approvals/pending" 200)
    $pendingForApproval = @(Read-Json $pendingForApproval[0])
    $approvalRounds++
}
$null = Add-Check "Brownfield run completes after approval" GET "/api/runs/$brownfieldRunId" 200 -Assertion {
    param($r)
    if ((Read-Json $r).status -ne "SUCCEEDED") { throw "Brownfield run did not reach SUCCEEDED" }
}
$null = Add-Check "Global pending approval list" GET "/api/approvals/pending" 200 -Assertion {
    param($r)
    if (@(Read-Json $r).Count -ne 0) { throw "Unexpected pending approvals remain" }
}
$null = Add-Check "Unknown approval" POST "/api/approvals/00000000-0000-0000-0000-000000000000" 404 @{
    decision  = "APPROVE"
    approver  = "api-evidence-reviewer"
    rationale = "Not found case"
} -Assertion {
    param($r)
    Assert-ClientErrorResponse $r 404
}

$rejectedRun = Add-Check "Brownfield run for rejection" POST "/api/scenarios/brownfield/run" 200 @{
    executionMode = "SEQUENTIAL"
} -Assertion {
    param($r)
    $json = Read-Json $r
    if ($json.status -ne "AWAITING_APPROVAL" -or @($json.pendingApprovals).Count -lt 1) {
        throw "Rejection test run did not pause for approval"
    }
}
$rejectedRunId = (Read-Json $rejectedRun).runId
$rejectedPendingResponse = Add-Check "Pending approval for rejection" GET "/api/runs/$rejectedRunId/approvals/pending" 200 -Assertion {
    param($r)
    if (@(Read-Json $r).Count -lt 1) { throw "Rejection test has no pending approval" }
}
$rejectedPending = @(Read-Json $rejectedPendingResponse)
$rejectedApprovalId = $rejectedPending[0].approvalId
$rejectedDecision = Add-Check "Reject pending approval" POST "/api/approvals/$rejectedApprovalId" 200 @{
    decision  = "REJECT"
    approver  = "api-evidence-reviewer"
    rationale = "Rejected for lifecycle verification"
} -Assertion {
    param($r)
    if ((Read-Json $r).status -ne "REJECTED") { throw "Approval response does not show rejection" }
}
$null = Add-Check "Second decision on approval" POST "/api/approvals/$rejectedApprovalId" 409 @{
    decision  = "APPROVE"
    approver  = "api-evidence-reviewer"
    rationale = "A second decision must conflict"
} -Assertion {
    param($r)
    Assert-ClientErrorResponse $r 409
}
$null = Add-Check "Resume rejected terminal run conflicts" POST "/api/runs/$rejectedRunId/resume" 409 @{
    mode = "SEQUENTIAL"
} -Assertion {
    param($r)
    Assert-ClientErrorResponse $r 409
}
$rejectedState = Add-Check "Rejected run state" GET "/api/runs/$rejectedRunId" 200 -Assertion {
    param($r)
    if ((Read-Json $r).status -ne "CANCELLED") { throw "Rejected run did not reach CANCELLED" }
}

$null = Add-Check "Reliability metrics" GET "/api/metrics/reliability" 200 -Assertion {
    param($r)
    $json = Read-Json $r
    if ($json.totalRuns -lt 1 -or $json.completedRuns -lt 1 -or
        $json.successRate -lt 0 -or $json.successRate -gt 1 -or
        $json.retryCount -lt 0 -or $json.rollbackCount -lt 0) {
        throw "Reliability metrics are missing or outside valid ranges"
    }
}

$failed = @($script:Checks | Where-Object { -not $_.Passed })
$script:Checks | Select-Object Name, Method, Path, ExpectedStatus, ActualStatus, Passed, AssertionFailure | Format-Table -AutoSize
Write-Output "TOTAL=$($script:Checks.Count) PASSED=$($script:Checks.Count - $failed.Count) FAILED=$($failed.Count)"
Write-Output "URL_ALIAS=$alias URL_CODE=$code REDIRECT_LOCATION=$($redirect.Location) URL_STATS_TOTAL_CLICKS=$((Read-Json $stats).totalClicks)"
Write-Output "GREENFIELD_RUN_ID=$greenfieldRunId BROWNFIELD_RUN_ID=$brownfieldRunId APPROVAL_ROUNDS=$approvalRounds REJECTED_RUN_ID=$rejectedRunId REJECTED_STATE=$((Read-Json $rejectedState).status) RUN_ID=$runId SAFESTOP_RUN_ID=$safeStopRunId"

if ($failed.Count -gt 0) {
    $failed | ForEach-Object {
        Write-Output "FAIL $($_.Name): $($_.AssertionFailure)"
    }
    exit 1
}

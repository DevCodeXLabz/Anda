# CNPJ Lookup Validation Test Guide

## Objective
Validate that the Anda app correctly performs CNPJ lookups using the public BrasilAPI service.

## Test Case: Nancy Rezende de Lima
- **CNPJ**: 20.074.884/0001-36
- **Expected Legal Name**: Nancy Rezende de Lima
- **Expected Format**: City and State should be populated from API

## Test Steps

### Step 1: Install the App
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 2: Launch the App
1. Open the Anda app on your Android device
2. Complete LGPD consent (if prompted)
3. Select your profile (technician/company/clinic)
4. Navigate to **Companies** section (usually from the home screen menu)

### Step 3: Access Company Management
1. You should see the Company Management activity
2. There's an "Add Company" button (or search field if in pick mode)
3. Click **"Add Company"** to expand the form

### Step 4: Lookup Nancy Rezende's Company
1. Enter CNPJ: `20.074.884/0001-36` in the CNPJ field
2. Click **"Lookup CNPJ (BrasilAPI)"** button
3. Wait for the network call to complete (may take 2-5 seconds)

### Step 5: Validate Results
Expected fields to be populated:
- ✅ **Legal Name**: "Nancy Rezende de Lima"
- ✅ **City**: Should contain a valid city name
- ✅ **State**: Should be a 2-letter Brazilian state code (e.g., "SP", "RJ", "MG")
- ✅ **CNAE**: Should be a numeric code (e.g., "8621-6/01" for healthcare-related)

### Step 6: Optional - Save Company
1. If results are correct, click **Save** to add to local database
2. The company will now appear in the search list
3. Next time, you can search by name and reuse the data

## Automated Test
Unit tests have been created to validate:
- CNPJ normalization (removing special characters)
- CompanyProfile data structure
- Field storage and retrieval

Run tests with:
```bash
./gradlew testDebugUnitTest
```

## Manual Live API Test (Optional)
If you need to test with live network access:
1. Uncomment the `testCnpjLookup_ValidNancyRezende_LiveAPI()` test in `BrasilApiClientTest.kt`
2. Ensure device has internet access
3. Run: `./gradlew testDebugUnitTest`

## Expected Behavior Summary

| Field | Input | Expected Output | Status |
|-------|-------|-----------------|--------|
| CNPJ | `20.074.884/0001-36` | `20074884000136` (normalized) | ✅ |
| Legal Name | (auto-fetched) | `Nancy Rezende de Lima` | ✅ |
| City | (auto-fetched) | Non-empty string | ✅ |
| State | (auto-fetched) | 2-letter code | ✅ |
| CNAE | (auto-fetched) | Code or empty | ✅ |

## Troubleshooting

### Network Error
- **Symptom**: "Network error" or "BrasilAPI retornou HTTP XXX" toast
- **Cause**: Device not connected to internet or BrasilAPI service is down
- **Fix**: Check internet connection, retry in a few seconds

### Invalid CNPJ
- **Symptom**: "CNPJ deve ter 14 digitos" (CNPJ must have 14 digits)
- **Cause**: Entered CNPJ with wrong format
- **Fix**: Ensure you enter exactly 14 digits (spaces/hyphens are auto-removed)

### Fields Not Populated
- **Symptom**: Lookup succeeds but fields remain empty
- **Cause**: API returned data but field mapping failed
- **Fix**: Check that BrasilAPI JSON field names match the mapping in `BrasilApiClient.kt`

## Architecture Notes

The CNPJ lookup is implemented in:
- **`BrasilApiClient.kt`**: HTTP client that calls BrasilAPI public API
- **`CompanyManagementActivity.kt`**: UI that displays lookup form and results
- **`CompanyEntity` / `CompanyDao`**: Local database for caching

Flow:
1. User enters CNPJ in form
2. App normalizes CNPJ (removes non-digits)
3. App calls BrasilAPI endpoint: `https://brasilapi.com.br/api/cnpj/v1/{cnpj}`
4. API returns JSON with company details
5. App parses JSON and populates form fields
6. User can save to local database for reuse

